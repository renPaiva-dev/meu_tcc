/**
 * Fila offline de identificações e feedbacks.
 *
 * Quando o aparelho está sem conexão, a identificação recebe um id local e vai para a fila.
 * O feedback de uma identificação ainda não enviada aponta para esse id local; ao processar a
 * fila, a identificação é enviada primeiro e o id do servidor é usado no feedback.
 */
import type { ClienteApi } from '../api/cliente';
import { ErroApi, ErroRede } from '../api/cliente';
import type { DadosFeedback, DadosIdentificacao } from '../api/tipos';
import { CHAVES, gravarJson, lerJson, remover } from './armazenamento';

/** Referência a uma identificação: já registrada no servidor ou ainda só local. */
export type RefIdentificacao = { idServidor: number } | { idLocal: string };

export type ItemFila =
  | { tipo: 'identificacao'; idLocal: string; dados: DadosIdentificacao; criadoEm: string }
  | { tipo: 'feedback'; idLocal: string; alvo: RefIdentificacao; dados: DadosFeedback; criadoEm: string };

export interface EstadoFila {
  itens: ItemFila[];
  /** idLocal da identificação → id no servidor (após o envio). */
  mapa: Record<string, number>;
}

export interface ResultadoProcessamento {
  enviados: number;
  descartados: number;
  pendentes: number;
  pontosGanhos: number;
}

/** Subconjunto do cliente usado pela fila (facilita os testes). */
export type ApiFila = Pick<ClienteApi, 'registrarIdentificacao' | 'enviarFeedback'>;

const ESTADO_VAZIO: EstadoFila = { itens: [], mapa: {} };

export function gerarIdLocal(): string {
  return `local-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export async function lerFila(): Promise<EstadoFila> {
  const estado = await lerJson<EstadoFila>(CHAVES.fila);
  return estado ?? { ...ESTADO_VAZIO, itens: [], mapa: {} };
}

async function gravarFila(estado: EstadoFila): Promise<void> {
  await gravarJson(CHAVES.fila, estado);
}

export async function limparFila(): Promise<void> {
  await remover(CHAVES.fila);
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

export function enfileirar(item: ItemFila): Promise<void> {
  return emSerie(async () => {
    const estado = await lerFila();
    estado.itens.push(item);
    await gravarFila(estado);
  });
}

/** Resolve uma referência para o id do servidor, se já conhecido. */
export async function resolverRef(ref: RefIdentificacao): Promise<number | null> {
  if ('idServidor' in ref) return ref.idServidor;
  const estado = await lerFila();
  return estado.mapa[ref.idLocal] ?? null;
}

/** Erros 4xx (exceto 401/408/429) são definitivos: reenviar não adianta. */
function ehErroDefinitivo(e: unknown): boolean {
  return (
    e instanceof ErroApi && e.status >= 400 && e.status < 500 && ![401, 408, 429].includes(e.status)
  );
}

/** Envia os itens pendentes em ordem. Para no primeiro erro de rede ou de autenticação. */
export function processarFila(api: ApiFila): Promise<ResultadoProcessamento> {
  return emSerie(async () => {
    const estado = await lerFila();
    const resultado: ResultadoProcessamento = { enviados: 0, descartados: 0, pendentes: 0, pontosGanhos: 0 };
    const restantes: ItemFila[] = [];
    let interromper = false;

    for (const item of estado.itens) {
      if (interromper) {
        restantes.push(item);
        continue;
      }
      try {
        if (item.tipo === 'identificacao') {
          const criada = await api.registrarIdentificacao(item.dados);
          estado.mapa[item.idLocal] = criada.id;
          resultado.enviados++;
        } else {
          const idServidor =
            'idServidor' in item.alvo ? item.alvo.idServidor : estado.mapa[item.alvo.idLocal];
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
        if (e instanceof ErroRede || (e instanceof ErroApi && !ehErroDefinitivo(e))) {
          restantes.push(item);
          interromper = true;
        } else {
          resultado.descartados++;
        }
      }
    }

    estado.itens = restantes;
    // O mapa só precisa guardar ids referenciados por feedbacks ainda pendentes.
    const referenciados = new Set(
      restantes.flatMap((i) => (i.tipo === 'feedback' && 'idLocal' in i.alvo ? [i.alvo.idLocal] : [])),
    );
    estado.mapa = Object.fromEntries(Object.entries(estado.mapa).filter(([k]) => referenciados.has(k)));
    resultado.pendentes = restantes.length;
    await gravarFila(estado);
    return resultado;
  });
}

// ---------- Operações "online primeiro, fila se offline" ----------

export interface ResultadoRegistro {
  ref: RefIdentificacao;
  enfileirado: boolean;
}

export async function registrarIdentificacao(
  api: ApiFila,
  dados: DadosIdentificacao,
): Promise<ResultadoRegistro> {
  try {
    const criada = await api.registrarIdentificacao(dados);
    return { ref: { idServidor: criada.id }, enfileirado: false };
  } catch (e) {
    if (!(e instanceof ErroRede)) throw e;
    const idLocal = gerarIdLocal();
    await enfileirar({ tipo: 'identificacao', idLocal, dados, criadoEm: new Date().toISOString() });
    return { ref: { idLocal }, enfileirado: true };
  }
}

export interface ResultadoFeedback {
  enfileirado: boolean;
  pontosGanhos: number;
}

/**
 * Envia o feedback; se offline (ou se a identificação ainda está na fila), enfileira.
 * `prepararParaFila` permite copiar a foto para um local permanente antes de enfileirar.
 */
export async function enviarFeedback(
  api: ApiFila,
  alvo: RefIdentificacao,
  dados: DadosFeedback,
  prepararParaFila?: (d: DadosFeedback) => Promise<DadosFeedback>,
): Promise<ResultadoFeedback> {
  const idServidor = await resolverRef(alvo);
  if (idServidor != null) {
    try {
      const r = await api.enviarFeedback(idServidor, dados);
      return { enfileirado: false, pontosGanhos: r.pontosGanhos };
    } catch (e) {
      if (!(e instanceof ErroRede)) throw e;
    }
  }
  const dadosFila = prepararParaFila ? await prepararParaFila(dados) : dados;
  await enfileirar({
    tipo: 'feedback',
    idLocal: gerarIdLocal(),
    alvo: idServidor != null ? { idServidor } : alvo,
    dados: dadosFila,
    criadoEm: new Date().toISOString(),
  });
  return { enfileirado: true, pontosGanhos: 0 };
}
