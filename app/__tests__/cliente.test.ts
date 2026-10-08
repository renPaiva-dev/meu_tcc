import { ClienteApi, ErroApi, ErroRede, mensagemDeErro } from '../src/api/cliente';

type Chamada = { url: string; init: RequestInit };

function respostaJson(status: number, corpo: unknown, tipo = 'application/json'): Response {
  return new Response(corpo === undefined ? null : JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': tipo },
  });
}

function criarCliente(respostas: (Response | Error)[], token: string | null = 'abc') {
  const chamadas: Chamada[] = [];
  const naoAutorizado = jest.fn();
  const fetchFalso = jest.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    chamadas.push({ url: String(url), init: init ?? {} });
    const r = respostas.shift();
    if (!r) throw new Error('sem resposta configurada');
    if (r instanceof Error) throw r;
    return r;
  });
  const cliente = new ClienteApi({
    baseUrl: 'http://api.teste',
    obterToken: () => token,
    aoNaoAutorizado: naoAutorizado,
    fetch: fetchFalso as unknown as typeof fetch,
  });
  return { cliente, chamadas, naoAutorizado };
}

describe('ClienteApi', () => {
  it('faz login enviando JSON e devolve token e usuário', async () => {
    const usuario = { id: 1, nome: 'Ana', email: 'a@b.com', zona: 'SUL', pontos: 0 };
    const { cliente, chamadas } = criarCliente([respostaJson(200, { token: 't', usuario })]);
    const r = await cliente.entrar('a@b.com', 'segredo');
    expect(r.usuario.nome).toBe('Ana');
    expect(chamadas[0].url).toBe('http://api.teste/api/v1/auth/login');
    expect(chamadas[0].init.method).toBe('POST');
    expect(JSON.parse(chamadas[0].init.body as string)).toEqual({ email: 'a@b.com', senha: 'segredo' });
    expect((chamadas[0].init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it('envia o Bearer nas rotas autenticadas', async () => {
    const { cliente, chamadas } = criarCliente([respostaJson(201, { id: 9, material: 'VIDRO' })]);
    await cliente.registrarIdentificacao({ material: 'VIDRO', confianca: 0.9 });
    expect((chamadas[0].init.headers as Record<string, string>).Authorization).toBe('Bearer abc');
  });

  it('converte problem+json 400 em ErroApi com campos', async () => {
    const { cliente } = criarCliente([
      respostaJson(
        400,
        { status: 400, detail: 'Dados inválidos', campos: { senha: 'tamanho deve ser entre 6 e 72' } },
        'application/problem+json',
      ),
    ]);
    const erro = await cliente.cadastrar({ nome: 'A', email: 'a@b.com', senha: '1' }).catch((e) => e);
    expect(erro).toBeInstanceOf(ErroApi);
    expect(erro.status).toBe(400);
    expect(erro.campos).toEqual({ senha: 'tamanho deve ser entre 6 e 72' });
    expect(mensagemDeErro(erro)).toContain('senha');
  });

  it('usa o detail do 422 (check-in longe do ponto)', async () => {
    const { cliente } = criarCliente([
      respostaJson(422, { status: 422, detail: 'Você está a 300 m do ponto' }, 'application/problem+json'),
    ]);
    const erro = await cliente.checkin(1, -5.08, -42.8).catch((e) => e);
    expect(erro).toBeInstanceOf(ErroApi);
    expect(erro.detalhe).toBe('Você está a 300 m do ponto');
  });

  it('avisa a sessão quando rota autenticada devolve 401', async () => {
    const { cliente, naoAutorizado } = criarCliente([respostaJson(401, { status: 401, detail: 'expirado' })]);
    await expect(cliente.eu()).rejects.toBeInstanceOf(ErroApi);
    expect(naoAutorizado).toHaveBeenCalledTimes(1);
  });

  it('não chama a rede sem token em rota autenticada', async () => {
    const { cliente, chamadas } = criarCliente([], null);
    await expect(cliente.historico()).rejects.toMatchObject({ status: 401 });
    expect(chamadas).toHaveLength(0);
  });

  it('transforma falha de rede em ErroRede', async () => {
    const { cliente } = criarCliente([new TypeError('Network request failed')]);
    await expect(cliente.materiais()).rejects.toBeInstanceOf(ErroRede);
  });

  it('localizarZona devolve null no 404 (bairros não importados)', async () => {
    const { cliente, chamadas } = criarCliente([respostaJson(404, { status: 404, detail: 'não encontrado' })]);
    await expect(cliente.localizarZona(-5.09, -42.8)).resolves.toBeNull();
    expect(chamadas[0].url).toBe('http://api.teste/api/v1/zonas/localizar?lat=-5.09&lon=-42.8');
  });

  it('monta a query de pontos próximos com filtro de material', async () => {
    const { cliente, chamadas } = criarCliente([respostaJson(200, [])]);
    await cliente.pontos({ lat: -5.09, lon: -42.8, raioKm: 10, material: 'VIDRO' });
    expect(chamadas[0].url).toBe('http://api.teste/api/v1/pontos?lat=-5.09&lon=-42.8&raioKm=10&material=VIDRO');
  });

  it('DELETE /me aceita 204 sem corpo', async () => {
    const { cliente, chamadas } = criarCliente([new Response(null, { status: 204 })]);
    await expect(cliente.excluirConta()).resolves.toBeUndefined();
    expect(chamadas[0].init.method).toBe('DELETE');
  });

  it('feedback vai como multipart sem foto quando não há consentimento', async () => {
    const { cliente, chamadas } = criarCliente([respostaJson(200, { pontosGanhos: 2 })]);
    const r = await cliente.enviarFeedback(5, {
      correto: false,
      materialCorreto: 'METAL',
      consentimentoFoto: false,
      foto: { uri: 'file:///x.jpg', nome: 'x.jpg', tipo: 'image/jpeg' },
    });
    expect(r.pontosGanhos).toBe(2);
    const corpo = chamadas[0].init.body as FormData;
    expect(corpo).toBeInstanceOf(FormData);
    expect(corpo.get('correto')).toBe('false');
    expect(corpo.get('materialCorreto')).toBe('METAL');
    expect(corpo.get('consentimentoFoto')).toBe('false');
    expect(corpo.get('foto')).toBeNull();
    // Content-Type fica a cargo do fetch (boundary do multipart)
    expect((chamadas[0].init.headers as Record<string, string>)['Content-Type']).toBeUndefined();
  });
});
