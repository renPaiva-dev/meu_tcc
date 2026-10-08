/** Instância única do cliente da API usada pelo app. */
import { API_URL, TEMPO_LIMITE_MS } from '../config';
import { ClienteApi } from './cliente';

let tokenAtual: string | null = null;
let ouvinteNaoAutorizado: (() => void) | null = null;

export function definirToken(token: string | null): void {
  tokenAtual = token;
}

/** Registra quem deve ser avisado quando o token expirar (a sessão faz logout). */
export function aoExpirarSessao(ouvinte: (() => void) | null): void {
  ouvinteNaoAutorizado = ouvinte;
}

export const api = new ClienteApi({
  baseUrl: API_URL,
  obterToken: () => tokenAtual,
  aoNaoAutorizado: () => ouvinteNaoAutorizado?.(),
  tempoLimiteMs: TEMPO_LIMITE_MS,
});
