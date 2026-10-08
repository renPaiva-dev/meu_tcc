import type { Previsao } from './preprocessamento';

export type OrigemClassificador = 'onnx' | 'desenvolvimento';

export interface ResultadoClassificacao {
  /** Classe mais provável. */
  melhor: Previsao;
  /** As 3 classes mais prováveis (ordem decrescente). */
  top3: Previsao[];
  /** Probabilidade de cada classe, na ordem de `classes` do labels.json. */
  probabilidades: number[];
  versaoModelo: string;
  origem: OrigemClassificador;
  duracaoMs: number;
}

/** Abstração do classificador de resíduos; a UI não sabe se é ONNX ou simulado. */
export interface Classificador {
  readonly origem: OrigemClassificador;
  readonly versaoModelo: string;
  /** Explica por que este classificador está em uso (ex.: motivo do modo desenvolvimento). */
  readonly descricao: string;
  /** Classifica a foto no caminho local `uri` (file://...). */
  classificar(uri: string): Promise<ResultadoClassificacao>;
}
