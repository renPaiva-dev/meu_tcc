/**
 * Classificador offline com ONNX Runtime (onnxruntime-react-native).
 *
 * Pipeline: foto → redimensiona para 224x224 sem preservar a proporção (expo-image-manipulator)
 * → JPEG → bytes (expo-file-system) → pixels RGBA (jpeg-js) → Float32Array NCHW normalizado
 * → sessão ONNX (entrada "input" [1,3,224,224]) → "logits" [1,7] → softmax.
 *
 * Requer development build: o módulo nativo não existe no Expo Go.
 */
import { Asset } from 'expo-asset';
import { File } from 'expo-file-system';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as jpeg from 'jpeg-js';
import type { InferenceSession, Tensor } from 'onnxruntime-react-native';

import { rgbaParaNCHW, softmax, topK, type Rotulos } from './preprocessamento';
import type { Classificador, ResultadoClassificacao } from './tipos';

type ModuloOrt = typeof import('onnxruntime-react-native');

const NOME_ENTRADA = 'input';
const NOME_SAIDA = 'logits';

/** Lê a foto e devolve os pixels RGBA já no tamanho do modelo. */
export async function carregarPixels(
  uri: string,
  tamanho: number,
): Promise<{ dados: Uint8Array; largura: number; altura: number }> {
  // Estica a foto inteira para tamanho x tamanho, sem recorte: é o mesmo Resize((224, 224))
  // usado na avaliação do modelo em ml/src/comum.py. Recortar aqui mudaria a distribuição
  // das imagens em relação ao treino.
  const contexto = ImageManipulator.manipulate(uri).resize({ width: tamanho, height: tamanho });
  const imagem = await contexto.renderAsync();
  const salvo = await imagem.saveAsync({ format: SaveFormat.JPEG, compress: 1 });
  const bytes = await new File(salvo.uri).bytes();
  const decodificado = jpeg.decode(bytes, { useTArray: true, formatAsRGBA: true });
  return { dados: decodificado.data, largura: decodificado.width, altura: decodificado.height };
}

export class ClassificadorOnnx implements Classificador {
  readonly origem = 'onnx' as const;
  readonly versaoModelo: string;
  readonly descricao: string;
  private readonly ort: ModuloOrt;
  private readonly sessao: InferenceSession;
  private readonly rotulos: Rotulos;

  private constructor(ort: ModuloOrt, sessao: InferenceSession, rotulos: Rotulos) {
    this.ort = ort;
    this.sessao = sessao;
    this.rotulos = rotulos;
    this.versaoModelo = rotulos.versao;
    this.descricao = `Modelo ${rotulos.versao} (offline, ONNX Runtime)`;
  }

  /** Carrega o asset do modelo e cria a sessão ONNX. Lança erro se algo faltar. */
  static async criar(ort: ModuloOrt, moduloModelo: number, rotulos: Rotulos): Promise<ClassificadorOnnx> {
    const asset = Asset.fromModule(moduloModelo);
    await asset.downloadAsync();
    const caminho = asset.localUri ?? asset.uri;
    const sessao = await ort.InferenceSession.create(caminho);
    if (!sessao.inputNames.includes(NOME_ENTRADA) || !sessao.outputNames.includes(NOME_SAIDA)) {
      throw new Error(
        `Modelo com nomes inesperados: entradas ${sessao.inputNames.join(',')}, saídas ${sessao.outputNames.join(',')}.`,
      );
    }
    return new ClassificadorOnnx(ort, sessao, rotulos);
  }

  async classificar(uri: string): Promise<ResultadoClassificacao> {
    const inicio = Date.now();
    const { tamanho, media, desvio } = this.rotulos.entrada;
    const { dados, largura, altura } = await carregarPixels(uri, tamanho);
    const entrada = rgbaParaNCHW(dados, largura, altura, media, desvio);
    const tensor: Tensor = new this.ort.Tensor('float32', entrada, [1, 3, altura, largura]);
    const saidas = await this.sessao.run({ [NOME_ENTRADA]: tensor });
    const logits = saidas[NOME_SAIDA].data as Float32Array;
    const probabilidades = softmax(logits);
    const top3 = topK(probabilidades, this.rotulos.classes, 3);
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
