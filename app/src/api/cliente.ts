/**
 * Cliente HTTP fino sobre fetch para a API ReciclaTHE.
 *
 * - Erros HTTP viram ErroApi (lê application/problem+json: status, detail, campos).
 * - Falhas de rede / tempo esgotado viram ErroRede (o app trata como "offline").
 * - Resposta 2xx que não é JSON (ex.: página de login de Wi-Fi público) também vira ErroRede.
 */
import type {
  ArquivoLocal,
  DadosCadastro,
  DadosFeedback,
  DadosIdentificacao,
  DadosSugestao,
  Historico,
  Identificacao,
  ItemRanking,
  LocalizacaoZona,
  Material,
  PontoColeta,
  RespostaToken,
  Usuario,
  Zona,
} from './tipos';

export class ErroApi extends Error {
  readonly status: number;
  readonly detalhe: string;
  readonly campos: Record<string, string>;

  constructor(status: number, detalhe: string, campos: Record<string, string> = {}) {
    super(detalhe);
    this.name = 'ErroApi';
    this.status = status;
    this.detalhe = detalhe;
    this.campos = campos;
  }
}

/** Sem conexão, servidor inacessível ou tempo esgotado. */
export class ErroRede extends Error {
  constructor(mensagem = 'Sem conexão com o servidor.') {
    super(mensagem);
    this.name = 'ErroRede';
  }
}

/** Mensagem amigável para exibir ao usuário a partir de qualquer erro. */
export function mensagemDeErro(e: unknown): string {
  if (e instanceof ErroApi) {
    const campos = Object.entries(e.campos);
    if (campos.length > 0) {
      return campos.map(([campo, msg]) => `${campo}: ${msg}`).join('\n');
    }
    return e.detalhe;
  }
  if (e instanceof ErroRede) return 'Sem conexão com o servidor. Tente novamente mais tarde.';
  if (e instanceof Error) return e.message;
  return 'Erro inesperado.';
}

const MENSAGENS_PADRAO: Record<number, string> = {
  400: 'Dados inválidos.',
  401: 'Sessão expirada ou credenciais inválidas.',
  403: 'Acesso negado.',
  404: 'Não encontrado.',
  409: 'Já existe um registro com esses dados.',
  413: 'Arquivo muito grande.',
  422: 'Operação não permitida.',
  429: 'Muitas tentativas. Aguarde um minuto e tente de novo.',
  500: 'Erro no servidor.',
};

export interface OpcoesCliente {
  baseUrl: string;
  /** Devolve o token JWT atual (ou null se não autenticado). */
  obterToken: () => Promise<string | null> | string | null;
  /** Chamado quando uma rota autenticada devolve 401 (token expirado). */
  aoNaoAutorizado?: () => void;
  fetch?: typeof fetch;
  tempoLimiteMs?: number;
}

type Metodo = 'GET' | 'POST' | 'PATCH' | 'DELETE';

interface OpcoesRequisicao {
  metodo?: Metodo;
  corpo?: unknown;
  formulario?: FormData;
  autenticado?: boolean;
}

/** Valor aceito pelo FormData do React Native para enviar um arquivo pelo caminho local. */
function arquivoParaFormData(arquivo: ArquivoLocal): Blob {
  // No React Native, FormData aceita {uri, name, type}; o tipo do DOM não conhece esse formato.
  return { uri: arquivo.uri, name: arquivo.nome, type: arquivo.tipo } as unknown as Blob;
}

export class ClienteApi {
  private readonly opcoes: OpcoesCliente;

  constructor(opcoes: OpcoesCliente) {
    this.opcoes = opcoes;
  }

  private async requisitar<T>(caminho: string, op: OpcoesRequisicao = {}): Promise<T> {
    const { metodo = 'GET', corpo, formulario, autenticado = false } = op;
    const cabecalhos: Record<string, string> = { Accept: 'application/json, application/problem+json' };
    let body: string | FormData | undefined;
    if (formulario) {
      // Não definir Content-Type: o fetch gera o boundary do multipart.
      body = formulario;
    } else if (corpo !== undefined) {
      cabecalhos['Content-Type'] = 'application/json';
      body = JSON.stringify(corpo);
    }
    if (autenticado) {
      const token = await this.opcoes.obterToken();
      if (!token) throw new ErroApi(401, 'É preciso entrar na sua conta.');
      cabecalhos.Authorization = `Bearer ${token}`;
    }

    const fetchImpl = this.opcoes.fetch ?? fetch;
    const controle = new AbortController();
    const temporizador = setTimeout(() => controle.abort(), this.opcoes.tempoLimiteMs ?? 15000);
    let resposta: Response;
    try {
      resposta = await fetchImpl(`${this.opcoes.baseUrl}${caminho}`, {
        method: metodo,
        headers: cabecalhos,
        body,
        signal: controle.signal,
      });
    } catch {
      // TypeError("Network request failed") ou AbortError: tratamos ambos como offline.
      throw new ErroRede();
    } finally {
      clearTimeout(temporizador);
    }

    if (!resposta.ok) {
      const erro = await this.lerProblema(resposta);
      if (erro.status === 401 && autenticado) this.opcoes.aoNaoAutorizado?.();
      throw erro;
    }
    if (resposta.status === 204) return undefined as T;
    let texto: string;
    try {
      texto = await resposta.text();
    } catch {
      throw new ErroRede();
    }
    if (!texto) return undefined as T;
    try {
      return JSON.parse(texto) as T;
    } catch {
      // Portal cativo/proxy devolvendo HTML com 200: não chegamos de fato à API.
      throw new ErroRede('Resposta inesperada do servidor. Verifique sua conexão.');
    }
  }

  private async lerProblema(resposta: Response): Promise<ErroApi> {
    let detalhe = MENSAGENS_PADRAO[resposta.status] ?? `Erro ${resposta.status}.`;
    let campos: Record<string, string> = {};
    try {
      const texto = await resposta.text();
      if (texto) {
        const json = JSON.parse(texto) as { detail?: unknown; campos?: unknown };
        if (typeof json.detail === 'string' && json.detail) detalhe = json.detail;
        if (json.campos && typeof json.campos === 'object') {
          campos = json.campos as Record<string, string>;
        }
      }
    } catch {
      // corpo não é JSON: fica a mensagem padrão
    }
    return new ErroApi(resposta.status, detalhe, campos);
  }

  // ---------- Autenticação e conta ----------

  cadastrar(dados: DadosCadastro): Promise<RespostaToken> {
    return this.requisitar('/api/v1/auth/cadastro', { metodo: 'POST', corpo: dados });
  }

  entrar(email: string, senha: string): Promise<RespostaToken> {
    return this.requisitar('/api/v1/auth/login', { metodo: 'POST', corpo: { email, senha } });
  }

  eu(): Promise<Usuario> {
    return this.requisitar('/api/v1/me', { autenticado: true });
  }

  alterarZona(zona: string | null): Promise<Usuario> {
    return this.requisitar('/api/v1/me', { metodo: 'PATCH', corpo: { zona }, autenticado: true });
  }

  excluirConta(): Promise<void> {
    return this.requisitar('/api/v1/me', { metodo: 'DELETE', autenticado: true });
  }

  historico(): Promise<Historico> {
    return this.requisitar('/api/v1/me/historico', { autenticado: true });
  }

  // ---------- Dados públicos (cache offline) ----------

  materiais(): Promise<Material[]> {
    return this.requisitar('/api/v1/materiais');
  }

  zonas(): Promise<Zona[]> {
    return this.requisitar('/api/v1/zonas');
  }

  /** Devolve null quando a API responde 404 (bairros ainda não importados). */
  async localizarZona(lat: number, lon: number): Promise<LocalizacaoZona | null> {
    try {
      return await this.requisitar(`/api/v1/zonas/localizar?lat=${lat}&lon=${lon}`);
    } catch (e) {
      if (e instanceof ErroApi && e.status === 404) return null;
      throw e;
    }
  }

  /** Sem coordenadas: todos os pontos aprovados (para o cache). */
  pontos(filtro?: { lat: number; lon: number; raioKm?: number; material?: string }): Promise<PontoColeta[]> {
    if (!filtro) return this.requisitar('/api/v1/pontos');
    const params = new URLSearchParams({
      lat: String(filtro.lat),
      lon: String(filtro.lon),
      raioKm: String(filtro.raioKm ?? 10),
    });
    if (filtro.material) params.set('material', filtro.material);
    return this.requisitar(`/api/v1/pontos?${params.toString()}`);
  }

  /** Um ponto aprovado (usado quando ele ainda não está no cache). */
  ponto(id: number | string): Promise<PontoColeta> {
    return this.requisitar(`/api/v1/pontos/${encodeURIComponent(String(id))}`);
  }

  ranking(zona?: string | null): Promise<ItemRanking[]> {
    const consulta = zona ? `?zona=${encodeURIComponent(zona)}` : '';
    return this.requisitar(`/api/v1/ranking${consulta}`);
  }

  // ---------- Ações autenticadas ----------

  sugerirPonto(dados: DadosSugestao): Promise<{ id: number; status: string }> {
    const form = new FormData();
    form.append('nome', dados.nome);
    form.append('tipo', dados.tipo);
    form.append('endereco', dados.endereco);
    form.append('latitude', String(dados.latitude));
    form.append('longitude', String(dados.longitude));
    if (dados.horario) form.append('horario', dados.horario);
    if (dados.telefone) form.append('telefone', dados.telefone);
    if (dados.fonte) form.append('fonte', dados.fonte);
    for (const m of dados.materiais) form.append('materiais', m);
    if (dados.foto) form.append('foto', arquivoParaFormData(dados.foto));
    return this.requisitar('/api/v1/pontos/sugestoes', { metodo: 'POST', formulario: form, autenticado: true });
  }

  checkin(idPonto: number, latitude: number, longitude: number): Promise<{ pontosGanhos: number }> {
    return this.requisitar(`/api/v1/pontos/${idPonto}/checkin`, {
      metodo: 'POST',
      corpo: { latitude, longitude },
      autenticado: true,
    });
  }

  registrarIdentificacao(dados: DadosIdentificacao): Promise<Identificacao> {
    return this.requisitar('/api/v1/identificacoes', { metodo: 'POST', corpo: dados, autenticado: true });
  }

  enviarFeedback(idIdentificacao: number, dados: DadosFeedback): Promise<{ pontosGanhos: number }> {
    const form = new FormData();
    form.append('correto', String(dados.correto));
    if (!dados.correto && dados.materialCorreto) form.append('materialCorreto', dados.materialCorreto);
    form.append('consentimentoFoto', String(dados.consentimentoFoto));
    // A foto só sai do aparelho com consentimento explícito.
    if (dados.consentimentoFoto && dados.foto) form.append('foto', arquivoParaFormData(dados.foto));
    return this.requisitar(`/api/v1/identificacoes/${idIdentificacao}/feedback`, {
      metodo: 'POST',
      formulario: form,
      autenticado: true,
    });
  }
}
