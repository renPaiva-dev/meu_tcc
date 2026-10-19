import AsyncStorage from '@react-native-async-storage/async-storage';

import { CHAVES } from '../src/armazenamento/armazenamento';
import { gravarToken, lerToken, removerToken } from '../src/armazenamento/token';

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

// SecureStore em memória (o módulo nativo não existe no Jest).
const mockCofre = new Map<string, string>();
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async (k: string) => mockCofre.get(k) ?? null),
  setItemAsync: jest.fn(async (k: string, v: string) => void mockCofre.set(k, v)),
  deleteItemAsync: jest.fn(async (k: string) => void mockCofre.delete(k)),
}));

beforeEach(async () => {
  mockCofre.clear();
  await AsyncStorage.clear();
});

describe('token no SecureStore', () => {
  it('migra uma vez o token antigo do AsyncStorage', async () => {
    await AsyncStorage.setItem(CHAVES.token, JSON.stringify('jwt-antigo'));
    expect(await lerToken()).toBe('jwt-antigo');
    expect(await AsyncStorage.getItem(CHAVES.token)).toBeNull();
    expect([...mockCofre.values()]).toEqual(['jwt-antigo']);
    expect(await lerToken()).toBe('jwt-antigo');
  });

  it('grava, lê e remove', async () => {
    await gravarToken('jwt');
    expect(await lerToken()).toBe('jwt');
    expect(await AsyncStorage.getItem(CHAVES.token)).toBeNull();
    await removerToken();
    expect(await lerToken()).toBeNull();
  });
});
