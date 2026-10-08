/**
 * Sincronização: baixa materiais, zonas e pontos aprovados para o cache offline e
 * envia a fila de identificações/feedbacks pendentes.
 */
import type { ClienteApi } from '../api/cliente';
import { gravarCache, type DadosCache } from '../armazenamento/cache';
import { processarFila, type ResultadoProcessamento } from '../armazenamento/fila';

export async function baixarDadosPublicos(api: Pick<ClienteApi, 'materiais' | 'zonas' | 'pontos'>): Promise<DadosCache> {
  const [materiais, zonas, pontos] = await Promise.all([api.materiais(), api.zonas(), api.pontos()]);
  const ultimaSincronizacao = await gravarCache({ materiais, zonas, pontos });
  return { materiais, zonas, pontos, ultimaSincronizacao };
}

export async function enviarPendentes(
  api: Pick<ClienteApi, 'registrarIdentificacao' | 'enviarFeedback'>,
): Promise<ResultadoProcessamento> {
  return processarFila(api);
}
