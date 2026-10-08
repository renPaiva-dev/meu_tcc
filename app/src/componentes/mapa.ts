/**
 * Carregamento sob demanda do mapa nativo (MapLibre).
 *
 * O Expo Go não inclui o módulo nativo do MapLibre; importá-lo lá derrubaria o app.
 * Por isso o módulo só é carregado fora do Expo Go; nas telas, sem mapa, mostramos a lista.
 */
import Constants, { ExecutionEnvironment } from 'expo-constants';

export const MAPA_DISPONIVEL = Constants.executionEnvironment !== ExecutionEnvironment.StoreClient;

type ModuloMapa = typeof import('./MapaBase');

let modulo: ModuloMapa | null | undefined;

/** Devolve os componentes de mapa, ou null se indisponíveis neste ambiente. */
export function carregarMapa(): ModuloMapa | null {
  if (modulo !== undefined) return modulo;
  if (!MAPA_DISPONIVEL) {
    modulo = null;
    return modulo;
  }
  try {
    modulo = require('./MapaBase') as ModuloMapa;
  } catch (e) {
    console.warn('[mapa] MapLibre indisponível', e);
    modulo = null;
  }
  return modulo;
}
