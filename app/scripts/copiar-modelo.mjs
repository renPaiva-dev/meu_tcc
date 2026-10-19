#!/usr/bin/env node
/**
 * Copia o modelo treinado (ml/modelo/reciclathe.onnx + labels.json) para assets/modelo/
 * e gera src/classificacao/modeloRegistrado.local.ts (ignorado pelo git) com os require(...).
 * O stub versionado modeloRegistrado.ts não é alterado.
 *
 * Uso: npm run copiar-modelo [-- caminho/da/pasta/modelo]
 * Padrão da origem: ../ml/modelo (relativo à pasta app/).
 */
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join, resolve } from 'node:path';

const require = createRequire(import.meta.url);
const { raizApp, pastaAssets, registroLocal, ARQUIVOS_MODELO } = require('./modelo-local.js');

const origem = resolve(process.argv[2] ?? join(raizApp, '..', 'ml', 'modelo'));

const faltando = ARQUIVOS_MODELO.filter((nome) => !existsSync(join(origem, nome)));
if (faltando.length > 0) {
  console.error(`Modelo não encontrado em ${origem}.`);
  for (const nome of faltando) console.error(`  - falta: ${nome}`);
  if (faltando.includes('reciclathe.onnx')) {
    console.error(
      '\nO reciclathe.onnx é gerado pelo projeto ml/ (treino + exportação para ONNX; veja ml/README.md)\n' +
        'e não é versionado. Gere-o lá antes, ou informe outra pasta: npm run copiar-modelo -- <pasta>.\n' +
        'Sem o modelo o app continua funcionando com o classificador de desenvolvimento (aleatório).',
    );
  }
  process.exit(1);
}

// Confere o formato do labels.json antes de copiar.
let rotulos;
try {
  rotulos = JSON.parse(readFileSync(join(origem, 'labels.json'), 'utf8'));
} catch (e) {
  console.error(`labels.json inválido: ${e.message}`);
  process.exit(1);
}
if (!Array.isArray(rotulos.classes) || !rotulos.entrada || rotulos.entrada.tamanho == null) {
  console.error('labels.json fora do formato esperado ({versao, classes, entrada:{tamanho, media, desvio, ...}}).');
  process.exit(1);
}
if (existsSync(join(origem, 'reciclathe.onnx.data'))) {
  console.warn('Aviso: há um reciclathe.onnx.data (pesos externos). Exporte o modelo com os pesos embutidos.');
}

mkdirSync(pastaAssets, { recursive: true });
for (const nome of ARQUIVOS_MODELO) {
  copyFileSync(join(origem, nome), join(pastaAssets, nome));
  console.log(`Copiado: ${nome} → assets/modelo/`);
}

writeFileSync(
  registroLocal,
  `/**
 * Registro do modelo empacotado no app — GERADO por \`npm run copiar-modelo\` em ${new Date().toISOString()}.
 * Ignorado pelo git. O metro.config.js usa este arquivo no lugar de modeloRegistrado.ts.
 * Modelo: ${rotulos.versao ?? '(sem versão)'} — classes: ${rotulos.classes.join(', ')}.
 * Para voltar ao modo desenvolvimento: \`npm run remover-modelo\`.
 */
/* eslint-disable @typescript-eslint/no-require-imports */

/** Módulo de asset do reciclathe.onnx (número devolvido pelo require do Metro) ou null. */
export const MODULO_MODELO: number | null = require('../../assets/modelo/reciclathe.onnx');

/** Conteúdo do labels.json ou null. */
export const ROTULOS_JSON: unknown = require('../../assets/modelo/labels.json');
`,
);
console.log('Registrado em src/classificacao/modeloRegistrado.local.ts. Reinicie o Metro com: npx expo start --clear');
