/**
 * CLASSIFICADOR DE DESENVOLVIMENTO — RESULTADOS ALEATÓRIOS (MOCK)
 *
 * Usado apenas quando o modelo ONNX não foi copiado para assets/modelo/ ou quando o
 * módulo nativo do onnxruntime não está disponível (ex.: Expo Go). Serve só para testar
 * o fluxo das telas; NÃO identifica nada de verdade. A UI mostra um aviso quando ele está ativo.
 */
import { CODIGOS_MATERIAL } from '../api/tipos';
import { softmax, topK } from './preprocessamento';
import type { Classificador, ResultadoClassificacao } from './tipos';

export class ClassificadorDesenvolvimento implements Classificador {
  readonly origem = 'desenvolvimento' as const;
  readonly versaoModelo = 'mock-aleatorio';
  readonly descricao: string;
  private readonly classes: readonly string[];
  private readonly aleatorio: () => number;

  constructor(motivo: string, classes: readonly string[] = CODIGOS_MATERIAL, aleatorio: () => number = Math.random) {
    this.descricao = `Modo desenvolvimento (resultado aleatório): ${motivo}`;
    this.classes = classes;
    this.aleatorio = aleatorio;
  }

  async classificar(_uri: string): Promise<ResultadoClassificacao> {
    const inicio = Date.now();
    // Logits aleatórios com escala variável: às vezes gera confiança alta, às vezes baixa,
    // para exercitar também a tela de "escolha entre as 3 mais prováveis".
    const escala = 1 + this.aleatorio() * 5;
    const logits = this.classes.map(() => this.aleatorio() * escala);
    const probabilidades = softmax(logits);
    const top3 = topK(probabilidades, this.classes, 3);
    await new Promise((r) => setTimeout(r, 300));
    return {
      melhor: top3[0],
      top3,
      probabilidades,
      versaoModelo: this.versaoModelo,
      origem: this.origem,
      duracaoMs: Date.now() - inicio,
    };
  }
}
