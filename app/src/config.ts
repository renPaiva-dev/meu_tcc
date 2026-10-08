/**
 * Configuração do app. Variáveis EXPO_PUBLIC_* são embutidas no bundle pelo Metro
 * (defina em app/.env ou no ambiente antes de `npx expo start`/`expo run:android`).
 */

/** 10.0.2.2 é o "localhost" da máquina hospedeira visto de dentro do emulador Android. */
const PADRAO_API = 'http://10.0.2.2:8080';

export const API_URL: string = (process.env.EXPO_PUBLIC_API_URL ?? PADRAO_API).replace(/\/+$/, '');

/** Abaixo deste valor o app mostra as 3 classes mais prováveis para o usuário escolher. */
export const LIMIAR_CONFIANCA = 0.6;

/** Tempo máximo de uma requisição antes de considerarmos o aparelho offline. */
export const TEMPO_LIMITE_MS = 15000;

/** Raio padrão para buscar pontos próximos. */
export const RAIO_BUSCA_KM = 10;

/** Centro aproximado de Teresina-PI (usado quando não há localização do usuário). */
export const CENTRO_TERESINA = { latitude: -5.0892, longitude: -42.8019 };

/** Retângulo aceito pela API para coordenadas de sugestões (DadosPonto.java). */
export const LIMITES_TERESINA = {
  latMin: -5.6,
  latMax: -4.75,
  lonMin: -43.05,
  lonMax: -42.55,
};
