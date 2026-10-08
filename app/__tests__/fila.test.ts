import AsyncStorage from '@react-native-async-storage/async-storage';

import { ErroApi, ErroRede } from '../src/api/cliente';
import type { DadosFeedback, DadosIdentificacao, Identificacao } from '../src/api/tipos';
import {
  contarPendentes,
  enviarFeedback,
  lerFila,
  processarFila,
  registrarIdentificacao,
  type ApiFila,
} from '../src/armazenamento/fila';

jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
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

beforeEach(async () => {
  await AsyncStorage.clear();
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
    expect(await lerFila()).toEqual({ itens: [], mapa: {} });
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

  it('erro que não é de rede no registro online é repassado (não enfileira)', async () => {
    const { api } = apiFalsa();
    api.registrarIdentificacao.mockRejectedValueOnce(new ErroApi(400, 'material inválido'));
    await expect(registrarIdentificacao(api, IDENT)).rejects.toBeInstanceOf(ErroApi);
    expect(await contarPendentes()).toBe(0);
  });
});
