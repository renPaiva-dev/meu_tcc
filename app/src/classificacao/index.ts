/**
 * Ponto de entrada da classificação: escolhe a implementação disponível.
 *
 * - ONNX: quando o modelo foi copiado (npm run copiar-modelo) E o módulo nativo existe
 *   (development build / APK).
 * - Desenvolvimento (aleatório): caso contrário, para o app continuar testável no Expo Go.
 */
import { NativeModules } from 'react-native';

import { ClassificadorDesenvolvimento } from './classificadorDesenvolvimento';
import { MODULO_MODELO, ROTULOS_JSON } from './modeloRegistrado';
import { validarRotulos } from './preprocessamento';
import type { Classificador } from './tipos';

export type { Classificador, ResultadoClassificacao } from './tipos';
export type { Previsao } from './preprocessamento';

let promessa: Promise<Classificador> | null = null;

async function criarClassificador(): Promise<Classificador> {
  if (MODULO_MODELO == null || ROTULOS_JSON == null) {
    return new ClassificadorDesenvolvimento('o modelo não foi copiado para assets/modelo (rode npm run copiar-modelo).');
  }
  let rotulos;
  try {
    rotulos = validarRotulos(ROTULOS_JSON);
  } catch (e) {
    return new ClassificadorDesenvolvimento(`labels.json inválido (${(e as Error).message}).`);
  }
  // O pacote onnxruntime-react-native chama o módulo nativo já no import; se ele não existir
  // (Expo Go), o import quebraria o app. Por isso verificamos antes e importamos sob demanda.
  if (!NativeModules.Onnxruntime) {
    return new ClassificadorDesenvolvimento(
      'o módulo nativo do ONNX Runtime não está disponível (Expo Go?). Use um development build.',
      rotulos.classes,
    );
  }
  try {
    const ort = await import('onnxruntime-react-native');
    const { ClassificadorOnnx } = await import('./classificadorOnnx');
    return await ClassificadorOnnx.criar(ort, MODULO_MODELO, rotulos);
  } catch (e) {
    console.warn('[classificacao] falha ao carregar o modelo ONNX', e);
    return new ClassificadorDesenvolvimento(
      `falha ao carregar o modelo ONNX (${(e as Error).message}).`,
      rotulos.classes,
    );
  }
}

/** Devolve (e memoriza) o classificador a ser usado no app. */
export function obterClassificador(): Promise<Classificador> {
  promessa ??= criarClassificador();
  return promessa;
}
