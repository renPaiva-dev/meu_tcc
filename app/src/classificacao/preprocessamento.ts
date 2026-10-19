/**
 * Funções puras de pré e pós-processamento do classificador (sem dependências nativas,
 * cobertas por testes unitários em __tests__/preprocessamento.test.ts).
 */

export interface ConfigEntrada {
  /** Lado da imagem quadrada esperada pelo modelo (ex.: 224). */
  tamanho: number;
  /** Média por canal (R, G, B) após dividir o pixel por 255. */
  media: [number, number, number];
  /** Desvio padrão por canal (R, G, B). */
  desvio: [number, number, number];
  layout: 'NCHW';
  escala: 'pixel/255';
}

export interface Rotulos {
  versao: string;
  classes: string[];
  entrada: ConfigEntrada;
}

/**
 * Converte pixels RGBA intercalados (como devolvidos pelo jpeg-js) em um tensor
 * float32 no layout NCHW [1, 3, altura, largura], normalizado como no treino:
 * valor = (pixel / 255 - media[c]) / desvio[c]. O canal alfa é descartado.
 */
export function rgbaParaNCHW(
  rgba: Uint8Array | Uint8ClampedArray | number[],
  largura: number,
  altura: number,
  media: readonly [number, number, number],
  desvio: readonly [number, number, number],
): Float32Array {
  const area = largura * altura;
  if (rgba.length < area * 4) {
    throw new Error(`Esperados ${area * 4} bytes RGBA, recebidos ${rgba.length}.`);
  }
  const saida = new Float32Array(3 * area);
  for (let i = 0; i < area; i++) {
    const p = i * 4;
    saida[i] = (rgba[p] / 255 - media[0]) / desvio[0]; // plano R
    saida[area + i] = (rgba[p + 1] / 255 - media[1]) / desvio[1]; // plano G
    saida[2 * area + i] = (rgba[p + 2] / 255 - media[2]) / desvio[2]; // plano B
  }
  return saida;
}

/** Softmax numericamente estável (subtrai o máximo antes da exponencial). */
export function softmax(logits: ArrayLike<number>): number[] {
  if (logits.length === 0) return [];
  let maximo = -Infinity;
  for (let i = 0; i < logits.length; i++) maximo = Math.max(maximo, logits[i]);
  const exps = Array.from(logits, (v) => Math.exp(v - maximo));
  const soma = exps.reduce((a, b) => a + b, 0);
  return exps.map((v) => v / soma);
}

export interface Previsao {
  classe: string;
  probabilidade: number;
}

/** As k classes mais prováveis, em ordem decrescente de probabilidade. */
export function topK(probabilidades: readonly number[], classes: readonly string[], k = 3): Previsao[] {
  if (probabilidades.length !== classes.length) {
    throw new Error('Quantidade de probabilidades difere da quantidade de classes.');
  }
  return classes
    .map((classe, i) => ({ classe, probabilidade: probabilidades[i] }))
    .sort((a, b) => b.probabilidade - a.probabilidade)
    .slice(0, k);
}

/** Valida o labels.json gerado pelo pipeline de ML. */
export function validarRotulos(json: unknown): Rotulos {
  const r = json as Partial<Rotulos> | null;
  if (
    !r ||
    typeof r.versao !== 'string' ||
    !Array.isArray(r.classes) ||
    r.classes.length === 0 ||
    !r.entrada ||
    typeof r.entrada.tamanho !== 'number' ||
    !Array.isArray(r.entrada.media) ||
    r.entrada.media.length !== 3 ||
    !Array.isArray(r.entrada.desvio) ||
    r.entrada.desvio.length !== 3
  ) {
    throw new Error('labels.json inválido.');
  }
  if (r.entrada.layout && r.entrada.layout !== 'NCHW') {
    throw new Error(`Layout ${String(r.entrada.layout)} não suportado (esperado NCHW).`);
  }
  return r as Rotulos;
}
