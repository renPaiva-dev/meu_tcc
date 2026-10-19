/**
 * Fila offline de identificações e feedbacks.
 *
 * Quando o aparelho está sem conexão (ou o servidor falha), a identificação recebe um id local
 * e vai para a fila. O feedback de uma identificação ainda não enviada aponta para esse id local;
 * ao processar a fila, a identificação é enviada primeiro e o id do servidor é usado no feedback.
 *
 * A fila pertence a um usuário (`dono`). Se a sessão expira, os itens ficam guardados e só são
 * enviados quando o mesmo usuário entrar de novo; se outro usuário entrar, são descartados.
 */
import type { ClienteApi } from '../api/cliente';
import { ErroApi, ErroRede } from '../api/cliente';
import type { DadosFeedback, DadosIdentificacao, Identificacao } from '../api/tipos';
import { CHAVES, gravarJson, lerJson, remover } from './armazenamento';

/** Referência a uma identificação: já registrada no servidor ou ainda só local. */
export type RefIdentificacao = { idServidor: number } | { idLocal: string };

export type ItemFila =
  | { tipo: 'identificacao'; idLocal: string; dados: DadosIdentificacao; criadoEm: string }
  | { tipo: 'feedback'; idLocal: string; alvo: RefIdentificacao; dados: DadosFeedback; criadoEm: string };

/** Identificação já enviada: id no servidor e quando foi enviada (ms). */
export interface Mapeamento {
  id: number;
  em: number;
}

export interface EstadoFila {
  /** Id do usuário dono dos itens (null enquanto a fila nunca foi usada). */
  dono: number | null;
  itens: ItemFila[];
  /**
   * idLocal da identificação → id no servidor. Fica guardado por um tempo depois do envio
   * para que um feedback dado mais tarde (tela de resultado ainda aberta) encontre o id.
   */
  mapa: Record<string, Mapeamento>;
}

export interface ResultadoProcessamento {
  enviados: number;
  descartados: number;
  pendentes: number;
  pontosGanhos: number;
}

/** Subconjunto do cliente usado pela fila (facilita os testes). */
export type ApiFila = Pick<ClienteApi, 'registrarIdentificacao' | 'enviarFeedback'>;

/** Por quanto tempo (e quantos) mapeamentos idLocal → idServidor são mantidos após o envio. */
export const VALIDADE_MAPA_MS = 30 * 24 * 60 * 60 * 1000;
export const MAXIMO_MAPA = 500;

/** Id local no formato UUID v4; também serve de chave para não enfileirar o mesmo item duas vezes. */
export function gerarIdLocal(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export async function lerFila(): Promise<EstadoFila> {
  const estado = await lerJson<Partial<EstadoFila>>(CHAVES.fila);
  // Versões anteriores guardavam o mapa como idLocal → número e não tinham dono.
  const mapa: Record<string, Mapeamento> = {};
  for (const [k, v] of Object.entries(estado?.mapa ?? {})) {
    if (typeof v === 'number') mapa[k] = { id: v, em: Date.now() };
    else if (v && typeof v.id === 'number') mapa[k] = v;
  }
  return { dono: estado?.dono ?? null, itens: estado?.itens ?? [], mapa };
}

async function gravarFila(estado: EstadoFila): Promise<void> {
  await gravarJson(CHAVES.fila, estado);
}

export async function contarPendentes(): Promise<number> {
  return (await lerFila()).itens.length;
}

// Serializa as alterações na fila para evitar condições de corrida entre telas e sincronização.
let corrente: Promise<unknown> = Promise.resolve();
function emSerie<T>(tarefa: () => Promise<T>): Promise<T> {
  const proxima = corrente.then(tarefa, tarefa);
  corrente = proxima.catch(() => undefined);
  return proxima;
}

// ---------- Dono da fila (sessão) ----------

let usuarioAtual: number | null = null;
/** Muda a cada troca de sessão; o processamento em andamento para quando ela muda. */
let geracaoSessao = 0;

/** Usuário da sessão atual (null = ninguém logado ou sessão expirada). */
export function definirUsuarioFila(id: number | null): void {
  if (id === usuarioAtual) return;
  usuarioAtual = id;
  geracaoSessao++;
}

/**
 * Chamado ao entrar (ou restaurar a sessão): se a fila guardada pertence a outro usuário,
 * ela é descartada. Devolve true quando descartou itens.
 */
export function associarFilaAoUsuario(id: number): Promise<boolean> {
  definirUsuarioFila(id);
  return emSerie(async () => {
    const estado = await lerFila();
    if (estado.dono === id) return false;
    const descartou = estado.dono != null && estado.itens.length > 0;
    await gravarFila(descartou ? { dono: id, itens: [], mapa: {} } : { ...estado, dono: id });
    return descartou;
  });
}

/** Apaga a fila (saída explícita, exclusão de conta ou troca de usuário). */
export function limparFila(): Promise<void> {
  return emSerie(() => remover(CHAVES.fila));
}

function enfileirar(item: ItemFila, dono: number | null): Promise<void> {
  return emSerie(async () => {
    if (dono == null) throw new Error('É preciso entrar na sua conta.');
    const estado = await lerFila();
    if (estado.dono != null && estado.dono !== dono) throw new Error('A sessão mudou; entre novamente.');
    estado.dono = dono;
    // Mesmo id local = mesmo item (ex.: nova tentativa depois de um tempo esgotado).
    if (!estado.itens.some((i) => i.idLocal === item.idLocal)) estado.itens.push(item);
    await gravarFila(estado);
  });
}

/** Resolve uma referência para o id do servidor, se já conhecido. */
export async function resolverRef(ref: RefIdentificacao): Promise<number | null> {
  if ('idServidor' in ref) return ref.idServidor;
  const estado = await lerFila();
  return estado.mapa[ref.idLocal]?.id ?? null;
}

/** Erros 4xx (exceto 401/408/429) são definitivos: reenviar não adianta. */
function ehErroDefinitivo(e: unknown): boolean {
  return (
    e instanceof ErroApi && e.status >= 400 && e.status < 500 && ![401, 408, 429].includes(e.status)
  );
}

/** Rede, tempo esgotado, 5xx, 401/408/429: vale tentar de novo mais tarde. */
function podeTentarDeNovo(e: unknown): boolean {
  return e instanceof ErroRede || (e instanceof ErroApi && !ehErroDefinitivo(e));
}

/** Uma resposta 200 sem id (ex.: página de login de Wi-Fi) não conta como registro. */
function conferirIdentificacao(criada: Identificacao | undefined): number {
  if (!criada || typeof criada.id !== 'number') throw new ErroRede('Resposta inesperada do servidor.');
  return criada.id;
}

/** Mantém os mapeamentos recentes (validade e quantidade máxima). */
function podarMapa(mapa: Record<string, Mapeamento>, agora: number): Record<string, Mapeamento> {
  return Object.fromEntries(
    Object.entries(mapa)
      .filter(([, m]) => agora - m.em < VALIDADE_MAPA_MS)
      .sort(([, a], [, b]) => b.em - a.em)
      .slice(0, MAXIMO_MAPA),
  );
}

/**
 * Envia os itens pendentes em ordem. Para no primeiro erro de rede/servidor/autenticação e
 * também se a sessão mudar no meio (saída, expiração ou troca de usuário).
 */
export function processarFila(api: ApiFila): Promise<ResultadoProcessamento> {
  return emSerie(async () => {
    const estado = await lerFila();
    const resultado: ResultadoProcessamento = { enviados: 0, descartados: 0, pendentes: 0, pontosGanhos: 0 };
    const dono = usuarioAtual;
    const geracao = geracaoSessao;
    if (dono == null || (estado.dono != null && estado.dono !== dono)) {
      resultado.pendentes = estado.itens.length;
      return resultado;
    }

    const pendentes = [...estado.itens];
    const restantes: ItemFila[] = [];
    let interromper = false;
    // Grava o progresso a cada item: se o app for fechado no meio, nada é enviado duas vezes.
    const salvar = () => gravarFila({ ...estado, itens: [...restantes, ...pendentes] });

    while (pendentes.length > 0) {
      const item = pendentes.shift()!;
      if (interromper || geracao !== geracaoSessao) {
        restantes.push(item);
        interromper = true;
        continue;
      }
      try {
        if (item.tipo === 'identificacao') {
          const id = conferirIdentificacao(await api.registrarIdentificacao(item.dados));
          estado.mapa[item.idLocal] = { id, em: Date.now() };
          resultado.enviados++;
        } else {
          const idServidor =
            'idServidor' in item.alvo ? item.alvo.idServidor : estado.mapa[item.alvo.idLocal]?.id;
          if (idServidor == null) {
            // A identificação de origem ainda não foi enviada (ou foi descartada).
            const origemNaFila = restantes.some(
              (i) => i.tipo === 'identificacao' && 'idLocal' in item.alvo && i.idLocal === item.alvo.idLocal,
            );
            if (origemNaFila) restantes.push(item);
            else resultado.descartados++;
            continue;
          }
          const r = await api.enviarFeedback(idServidor, item.dados);
          resultado.pontosGanhos += r.pontosGanhos;
          resultado.enviados++;
        }
      } catch (e) {
        if (podeTentarDeNovo(e)) {
          restantes.push(item);
          interromper = true;
        } else {
          resultado.descartados++;
        }
      }
      await salvar();
    }

    estado.itens = restantes;
    estado.mapa = podarMapa(estado.mapa, Date.now());
    resultado.pendentes = restantes.length;
    await gravarFila(estado);
    return resultado;
  });
}

// ---------- Operações "online primeiro, fila se falhar" ----------

export interface ResultadoRegistro {
  ref: RefIdentificacao;
  enfileirado: boolean;
}

export async function registrarIdentificacao(
  api: ApiFila,
  dados: DadosIdentificacao,
): Promise<ResultadoRegistro> {
  // O dono é lido antes da chamada: um 401 encerra a sessão, mas o item continua sendo dele.
  const dono = usuarioAtual;
  try {
    const id = conferirIdentificacao(await api.registrarIdentificacao(dados));
    return { ref: { idServidor: id }, enfileirado: false };
  } catch (e) {
    if (!podeTentarDeNovo(e)) throw e;
    const idLocal = gerarIdLocal();
    await enfileirar({ tipo: 'identificacao', idLocal, dados, criadoEm: new Date().toISOString() }, dono);
    return { ref: { idLocal }, enfileirado: true };
  }
}

export interface ResultadoFeedback {
  enfileirado: boolean;
  pontosGanhos: number;
}

/**
 * Envia o feedback; se falhar por rede/servidor (ou se a identificação ainda está na fila),
 * enfileira. `prepararParaFila` permite copiar a foto para um local permanente antes de enfileirar.
 * Se a identificação de origem não existe mais (nem no servidor, nem na fila), lança erro em vez
 * de enfileirar um item que seria descartado.
 */
export async function enviarFeedback(
  api: ApiFila,
  alvo: RefIdentificacao,
  dados: DadosFeedback,
  prepararParaFila?: (d: DadosFeedback) => Promise<DadosFeedback>,
): Promise<ResultadoFeedback> {
  const dono = usuarioAtual;
  // Resolve em série: se a fila estiver sendo enviada agora, espera ela terminar.
  const { idServidor, origemNaFila } = await emSerie(async () => {
    const estado = await lerFila();
    if ('idServidor' in alvo) return { idServidor: alvo.idServidor, origemNaFila: false };
    return {
      idServidor: estado.mapa[alvo.idLocal]?.id ?? null,
      origemNaFila: estado.itens.some((i) => i.tipo === 'identificacao' && i.idLocal === alvo.idLocal),
    };
  });
  if (idServidor != null) {
    try {
      const r = await api.enviarFeedback(idServidor, dados);
      return { enfileirado: false, pontosGanhos: r.pontosGanhos };
    } catch (e) {
      if (!podeTentarDeNovo(e)) throw e;
    }
  } else if (!origemNaFila) {
    throw new Error('A identificação não foi registrada; não é possível enviar a resposta.');
  }
  const dadosFila = prepararParaFila ? await prepararParaFila(dados) : dados;
  await enfileirar(
    {
      tipo: 'feedback',
      idLocal: gerarIdLocal(),
      alvo: idServidor != null ? { idServidor } : alvo,
      dados: dadosFila,
      criadoEm: new Date().toISOString(),
    },
    dono,
  );
  return { enfileirado: true, pontosGanhos: 0 };
}
