/**
 * Guarda em memória a foto e o resultado da classificação em andamento, para passar da
 * aba Identificar para a tela de resultado sem serializar tudo em parâmetros de rota.
 */
import type { ResultadoClassificacao } from '../classificacao';

export interface IdentificacaoAtual {
  uri: string;
  resultado: ResultadoClassificacao;
  descricaoClassificador: string;
}

let atual: IdentificacaoAtual | null = null;

export function definirIdentificacaoAtual(valor: IdentificacaoAtual | null): void {
  atual = valor;
}

export function obterIdentificacaoAtual(): IdentificacaoAtual | null {
  return atual;
}
