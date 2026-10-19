/**
 * Registro do modelo empacotado no app — STUB VERSIONADO (não edite).
 *
 * Os arquivos do modelo (assets/modelo/reciclathe.onnx e labels.json) não estão no git. O
 * `npm run copiar-modelo` copia os arquivos e gera `modeloRegistrado.local.ts` (ignorado pelo
 * git) com os `require(...)`. O metro.config.js usa o arquivo local no lugar deste quando ele e
 * os assets existem; caso contrário fica este stub, os valores são null e o app usa o
 * classificador de desenvolvimento (aleatório).
 *
 * (O Metro resolve `require` em tempo de build; por isso não dá para fazer um require
 * condicional de um arquivo que pode não existir.)
 */

/** Módulo de asset do reciclathe.onnx (número devolvido pelo require do Metro) ou null. */
export const MODULO_MODELO: number | null = null;

/** Conteúdo do labels.json ou null. */
export const ROTULOS_JSON: unknown = null;
