/**
 * Token JWT guardado no armazenamento seguro do sistema (Keystore no Android, Keychain no iOS)
 * com expo-secure-store. Versões anteriores guardavam o token no AsyncStorage; na primeira
 * leitura ele é migrado e apagado de lá.
 */
import * as SecureStore from 'expo-secure-store';

import { CHAVES, lerJson, remover } from './armazenamento';

/** Chaves do SecureStore aceitam apenas letras, números, ".", "-" e "_". */
const CHAVE_SEGURA = 'reciclathe.token';

export async function lerToken(): Promise<string | null> {
  try {
    const token = await SecureStore.getItemAsync(CHAVE_SEGURA);
    if (token) return token;
  } catch {
    // chave invalidada (ex.: backup restaurado em outro aparelho): trata como sem sessão
  }
  const antigo = await lerJson<string>(CHAVES.token);
  if (!antigo) return null;
  try {
    await SecureStore.setItemAsync(CHAVE_SEGURA, antigo);
    await remover(CHAVES.token);
  } catch {
    // se a migração falhar, tenta de novo na próxima abertura
  }
  return antigo;
}

export async function gravarToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(CHAVE_SEGURA, token);
}

export async function removerToken(): Promise<void> {
  try {
    await SecureStore.deleteItemAsync(CHAVE_SEGURA);
  } finally {
    await remover(CHAVES.token);
  }
}
