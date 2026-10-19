import AsyncStorage from '@react-native-async-storage/async-storage';

import { ErroApi, ErroRede } from '../src/api/cliente';
import type { DadosFeedback, DadosIdentificacao, Identificacao } from '../src/api/tipos';
import {
  associarFilaAoUsuario,
  contarPendentes,
  definirUsuarioFila,
  enviarFeedback,
  lerFila,
  limparFila,
  processarFila,
  registrarIdentificacao,
  VALIDADE_MAPA_MS,
  type ApiFila,
} from '../src/armazenamento/fila';

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

const IDENT: DadosIdentificacao = { material: 'VIDRO', confianca: 0.82, zona: 'SUL', versaoModelo: 'v1' };
const FEEDBACK: DadosFeedback = { correto: true, consentimentoFoto: false };

function identificacao(id: number): Identificacao {
  return {
    id,
    material: 'VIDRO',
    confianca: 0.82,
    zona: 'SUL',
    feedbackCorreto: null,
    materialCorrigido: null,
    criadoEm: new Date().toISOString(),
  };
}

/** API falsa: `online` controla se as chamadas falham por rede. */
function apiFalsa() {
  const estado = { online: true, proximoId: 100 };
  const api = {
    registrarIdentificacao: jest.fn(async (_d: DadosIdentificacao) => {
      if (!estado.online) throw new ErroRede();
      return identificacao(estado.proximoId++);
    }),
    enviarFeedback: jest.fn(async (_id: number, _d: DadosFeedback) => {
      if (!estado.online) throw new ErroRede();
      return { pontosGanhos: 2 };
    }),
  };
  return { api: api as ApiFila & typeof api, estado };
}

const USUARIO = 1;

beforeEach(async () => {
  definirUsuarioFila(null);
  await AsyncStorage.clear();
  await associarFilaAoUsuario(USUARIO);
});

describe('fila offline', () => {
  it('online: registra direto e não enfileira', async () => {
    const { api } = apiFalsa();
    const r = await registrarIdentificacao(api, IDENT);
    expect(r).toEqual({ ref: { idServidor: 100 }, enfileirado: false });
    expect(await contarPendentes()).toBe(0);
  });

  it('offline: enfileira identificação e feedback, e envia na ordem quando volta a conexão', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;

    const reg = await registrarIdentificacao(api, IDENT);
    expect(reg.enfileirado).toBe(true);
    expect('idLocal' in reg.ref).toBe(true);

    const fb = await enviarFeedback(api, reg.ref, FEEDBACK);
    expect(fb).toEqual({ enfileirado: true, pontosGanhos: 0 });
    expect(await contarPendentes()).toBe(2);

    // Ainda offline: nada sai da fila.
    let r = await processarFila(api);
    expect(r).toMatchObject({ enviados: 0, pendentes: 2 });

    estado.online = true;
    r = await processarFila(api);
    expect(r).toEqual({ enviados: 2, descartados: 0, pendentes: 0, pontosGanhos: 2 });
    // O feedback usou o id que o servidor deu à identificação.
    expect(api.enviarFeedback).toHaveBeenLastCalledWith(100, FEEDBACK);
    const fila = await lerFila();
    expect(fila.itens).toEqual([]);
    expect(fila.dono).toBe(USUARIO);
  });

  it('feedback de identificação já registrada usa o id do servidor; offline enfileira', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    const fb = await enviarFeedback(api, { idServidor: 7 }, FEEDBACK);
    expect(fb.enfileirado).toBe(true);
    estado.online = true;
    await processarFila(api);
    expect(api.enviarFeedback).toHaveBeenLastCalledWith(7, FEEDBACK);
  });

  it('o prepararParaFila é aplicado só quando o item vai para a fila', async () => {
    const { api, estado } = apiFalsa();
    const preparar = jest.fn(async (d: DadosFeedback) => ({ ...d, foto: null }));
    await enviarFeedback(api, { idServidor: 1 }, FEEDBACK, preparar);
    expect(preparar).not.toHaveBeenCalled();
    estado.online = false;
    await enviarFeedback(api, { idServidor: 1 }, FEEDBACK, preparar);
    expect(preparar).toHaveBeenCalledTimes(1);
  });

  it('descarta itens com erro definitivo (4xx) e o feedback que dependia deles', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    const reg = await registrarIdentificacao(api, IDENT);
    await enviarFeedback(api, reg.ref, FEEDBACK);
    estado.online = true;
    api.registrarIdentificacao.mockRejectedValueOnce(new ErroApi(400, 'Dados inválidos'));
    const r = await processarFila(api);
    expect(r).toMatchObject({ enviados: 0, descartados: 2, pendentes: 0 });
  });

  it('para no primeiro erro de servidor (5xx) e mantém o restante', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    await registrarIdentificacao(api, IDENT);
    await registrarIdentificacao(api, IDENT);
    estado.online = true;
    api.registrarIdentificacao.mockClear();
    api.registrarIdentificacao.mockRejectedValueOnce(new ErroApi(503, 'indisponível'));
    const r = await processarFila(api);
    expect(r).toMatchObject({ enviados: 0, pendentes: 2 });
    expect(api.registrarIdentificacao).toHaveBeenCalledTimes(1);
  });

  it('erro definitivo (4xx) no registro online é repassado (não enfileira)', async () => {
    const { api } = apiFalsa();
    api.registrarIdentificacao.mockRejectedValueOnce(new ErroApi(400, 'material inválido'));
    await expect(registrarIdentificacao(api, IDENT)).rejects.toBeInstanceOf(ErroApi);
    expect(await contarPendentes()).toBe(0);
  });

  it('5xx no primeiro registro enfileira (não perde a identificação)', async () => {
    const { api } = apiFalsa();
    api.registrarIdentificacao.mockRejectedValueOnce(new ErroApi(502, 'Bad Gateway'));
    const r = await registrarIdentificacao(api, IDENT);
    expect(r.enfileirado).toBe(true);
    expect(await contarPendentes()).toBe(1);
    // O feedback dessa identificação também pode ser dado (vai para a fila).
    const fb = await enviarFeedback(api, r.ref, FEEDBACK);
    expect(fb.enfileirado).toBe(true);
    await processarFila(api);
    expect(api.enviarFeedback).toHaveBeenLastCalledWith(100, FEEDBACK);
  });

  it('resposta 200 sem id (portal cativo) é tratada como falha de rede', async () => {
    const { api } = apiFalsa();
    api.registrarIdentificacao.mockResolvedValueOnce(undefined as unknown as Identificacao);
    const r = await registrarIdentificacao(api, IDENT);
    expect(r.enfileirado).toBe(true);
  });

  it('feedback dado depois da sincronização ainda encontra o id do servidor', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    const reg = await registrarIdentificacao(api, IDENT);
    estado.online = true;
    await processarFila(api); // envia a identificação; a tela de resultado ainda tem o id local
    expect(await contarPendentes()).toBe(0);

    const fb = await enviarFeedback(api, reg.ref, FEEDBACK);
    expect(fb).toEqual({ enfileirado: false, pontosGanhos: 2 });
    expect(api.enviarFeedback).toHaveBeenLastCalledWith(100, FEEDBACK);

    // Também se o feedback tardio for dado offline: enfileira e depois resolve pelo mapa.
    estado.online = false;
    const fb2 = await enviarFeedback(api, reg.ref, FEEDBACK);
    expect(fb2.enfileirado).toBe(true);
    estado.online = true;
    const r = await processarFila(api);
    expect(r).toMatchObject({ enviados: 1, descartados: 0, pendentes: 0 });
  });

  it('o mapa idLocal → id expira depois da validade', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    const reg = await registrarIdentificacao(api, IDENT);
    estado.online = true;
    const agora = Date.now();
    const espiao = jest.spyOn(Date, 'now');
    espiao.mockReturnValue(agora - VALIDADE_MAPA_MS - 1000);
    await processarFila(api); // mapeamento gravado com data antiga…
    espiao.mockReturnValue(agora);
    await processarFila(api); // …e podado agora
    espiao.mockRestore();
    // Sem o mapeamento nem a identificação na fila, o app avisa em vez de prometer o envio.
    await expect(enviarFeedback(api, reg.ref, FEEDBACK)).rejects.toThrow(/não foi registrada/);
    expect(await contarPendentes()).toBe(0);
  });
});

describe('fila e sessão', () => {
  it('sessão expirada mantém a fila; o mesmo usuário ao entrar de novo envia', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    await registrarIdentificacao(api, IDENT);
    estado.online = true;

    definirUsuarioFila(null); // token expirou (401)
    let r = await processarFila(api);
    expect(r).toMatchObject({ enviados: 0, pendentes: 1 });
    expect(api.registrarIdentificacao).toHaveBeenCalledTimes(1); // só a tentativa offline

    expect(await associarFilaAoUsuario(USUARIO)).toBe(false); // entrou de novo, mesma conta
    r = await processarFila(api);
    expect(r).toMatchObject({ enviados: 1, pendentes: 0 });
  });

  it('outro usuário ao entrar descarta a fila do anterior', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    await registrarIdentificacao(api, IDENT);
    definirUsuarioFila(null);
    expect(await associarFilaAoUsuario(2)).toBe(true);
    expect(await contarPendentes()).toBe(0);
    estado.online = true;
    await processarFila(api);
    expect(api.registrarIdentificacao).toHaveBeenCalledTimes(1);
  });

  it('item de um 401 continua pertencendo ao usuário que fez a ação', async () => {
    const { api } = apiFalsa();
    api.registrarIdentificacao.mockImplementationOnce(async () => {
      definirUsuarioFila(null); // o 401 encerra a sessão antes de o erro chegar aqui
      throw new ErroApi(401, 'expirado');
    });
    const r = await registrarIdentificacao(api, IDENT);
    expect(r.enfileirado).toBe(true);
    expect((await lerFila()).dono).toBe(USUARIO);
  });

  it('limparFila espera o envio em andamento, que para quando a sessão muda', async () => {
    const { api, estado } = apiFalsa();
    estado.online = false;
    await registrarIdentificacao(api, IDENT);
    await registrarIdentificacao(api, IDENT);
    estado.online = true;
    api.registrarIdentificacao.mockClear();

    let liberar!: () => void;
    api.registrarIdentificacao.mockImplementationOnce(
      () => new Promise<Identificacao>((ok) => (liberar = () => ok(identificacao(500)))),
    );
    const envio = processarFila(api);
    await new Promise((r) => setTimeout(r, 0)); // processarFila chegou à primeira chamada
    // Usuário sai enquanto o primeiro item está "no ar".
    definirUsuarioFila(null);
    const limpeza = limparFila();
    liberar();
    const r = await envio;
    await limpeza;

    expect(r).toMatchObject({ enviados: 1, pendentes: 1 });
    expect(api.registrarIdentificacao).toHaveBeenCalledTimes(1); // o segundo não foi enviado
    expect(await contarPendentes()).toBe(0); // e a limpeza não foi sobrescrita
  });
});
