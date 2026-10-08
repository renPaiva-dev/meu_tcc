/** Cache offline dos dados públicos: materiais, zonas e pontos aprovados. */
import type { Material, PontoColeta, Zona } from '../api/tipos';
import { CHAVES, gravarJson, lerJson } from './armazenamento';

export interface DadosCache {
  materiais: Material[];
  zonas: Zona[];
  pontos: PontoColeta[];
  /** ISO 8601 da última sincronização bem-sucedida (null se nunca sincronizou). */
  ultimaSincronizacao: string | null;
}

export async function lerCache(): Promise<DadosCache> {
  const [materiais, zonas, pontos, ultimaSincronizacao] = await Promise.all([
    lerJson<Material[]>(CHAVES.materiais),
    lerJson<Zona[]>(CHAVES.zonas),
    lerJson<PontoColeta[]>(CHAVES.pontos),
    lerJson<string>(CHAVES.ultimaSincronizacao),
  ]);
  return {
    materiais: materiais ?? [],
    zonas: zonas ?? [],
    pontos: pontos ?? [],
    ultimaSincronizacao,
  };
}

export async function gravarCache(dados: Omit<DadosCache, 'ultimaSincronizacao'>): Promise<string> {
  const agora = new Date().toISOString();
  await Promise.all([
    gravarJson(CHAVES.materiais, dados.materiais),
    gravarJson(CHAVES.zonas, dados.zonas),
    gravarJson(CHAVES.pontos, dados.pontos),
    gravarJson(CHAVES.ultimaSincronizacao, agora),
  ]);
  return agora;
}
