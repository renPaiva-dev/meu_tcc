#!/usr/bin/env node
/** Desfaz o `copiar-modelo`: volta o registro para null (modo desenvolvimento). */
import { rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raizApp = resolve(dirname(fileURLToPath(import.meta.url)), '..');
for (const nome of ['reciclathe.onnx', 'labels.json']) {
  rmSync(join(raizApp, 'assets', 'modelo', nome), { force: true });
}
writeFileSync(
  join(raizApp, 'src', 'classificacao', 'modeloRegistrado.ts'),
  `/**
 * Registro do modelo empacotado no app.
 *
 * ESTE ARQUIVO É REESCRITO pelo script \`npm run copiar-modelo\`, que copia
 * ml/modelo/reciclathe.onnx e ml/modelo/labels.json para assets/modelo/ e troca os
 * valores abaixo por \`require(...)\` dos arquivos copiados. Enquanto o modelo não for
 * copiado, ambos ficam null e o app usa o classificador de desenvolvimento (aleatório).
 *
 * (O Metro resolve \`require\` em tempo de build; por isso não dá para fazer um require
 * condicional de um arquivo que pode não existir.)
 */

/** Módulo de asset do reciclathe.onnx (número devolvido pelo require do Metro) ou null. */
export const MODULO_MODELO: number | null = null;

/** Conteúdo do labels.json ou null. */
export const ROTULOS_JSON: unknown = null;
`,
);
console.log('Modelo removido; o app volta a usar o classificador de desenvolvimento.');
