/** Localização atual do aparelho (expo-location), pedindo permissão quando preciso. */
import * as Location from 'expo-location';

import type { Coordenadas } from './geo';

export class ErroLocalizacao extends Error {}

export async function pedirPermissaoLocalizacao(): Promise<boolean> {
  const atual = await Location.getForegroundPermissionsAsync();
  if (atual.granted) return true;
  if (!atual.canAskAgain) return false;
  const pedido = await Location.requestForegroundPermissionsAsync();
  return pedido.granted;
}

/**
 * Devolve a posição atual. A coordenada é usada só no momento (busca de pontos, check-in,
 * sugestão); o app não a armazena.
 */
export async function obterLocalizacaoAtual(): Promise<Coordenadas> {
  const permitido = await pedirPermissaoLocalizacao();
  if (!permitido) {
    throw new ErroLocalizacao('Permissão de localização negada. Ative-a nas configurações do aparelho.');
  }
  try {
    const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
    return { latitude: pos.coords.latitude, longitude: pos.coords.longitude };
  } catch {
    throw new ErroLocalizacao('Não foi possível obter sua localização. Verifique se o GPS está ligado.');
  }
}
