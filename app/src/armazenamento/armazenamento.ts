/** Acesso tipado ao AsyncStorage (valores serializados em JSON). */
import AsyncStorage from '@react-native-async-storage/async-storage';

export const CHAVES = {
  /** Só para migrar versões antigas: o token agora fica no SecureStore (token.ts). */
  token: 'reciclathe:token',
  usuario: 'reciclathe:usuario',
  materiais: 'reciclathe:cache:materiais',
  zonas: 'reciclathe:cache:zonas',
  pontos: 'reciclathe:cache:pontos',
  ultimaSincronizacao: 'reciclathe:cache:ultimaSincronizacao',
  fila: 'reciclathe:fila',
} as const;

export async function lerJson<T>(chave: string): Promise<T | null> {
  try {
    const bruto = await AsyncStorage.getItem(chave);
    return bruto == null ? null : (JSON.parse(bruto) as T);
  } catch {
    return null;
  }
}

export async function gravarJson(chave: string, valor: unknown): Promise<void> {
  await AsyncStorage.setItem(chave, JSON.stringify(valor));
}

export async function remover(...chaves: string[]): Promise<void> {
  await AsyncStorage.multiRemove(chaves);
}
