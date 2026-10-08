#!/usr/bin/env node
/**
 * Copia o modelo treinado (ml/modelo/reciclathe.onnx + labels.json) para assets/modelo/
 * e registra os arquivos em src/classificacao/modeloRegistrado.ts.
 *
 * Uso: npm run copiar-modelo [-- caminho/da/pasta/modelo]
 * Padrão da origem: ../ml/modelo (relativo à pasta app/).
 */
import { copyFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raizApp = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const origem = resolve(process.argv[2] ?? join(raizApp, '..', 'ml', 'modelo'));
const destino = join(raizApp, 'assets', 'modelo');
const registro = join(raizApp, 'src', 'classificacao', 'modeloRegistrado.ts');

const arquivos = ['reciclathe.onnx', 'labels.json'];
for (const nome of arquivos) {
  if (!existsSync(join(origem, nome))) {
    console.error(`Arquivo não encontrado: ${join(origem, nome)}`);
    console.error('Gere o modelo no projeto ml/ antes (ou informe a pasta: npm run copiar-modelo -- <pasta>).');
    process.exit(1);
  }
}

// Confere o formato do labels.json antes de copiar.
const rotulos = JSON.parse(readFileSync(join(origem, 'labels.json'), 'utf8'));
if (!Array.isArray(rotulos.classes) || !rotulos.entrada || rotulos.entrada.tamanho == null) {
  console.error('labels.json fora do formato esperado ({versao, classes, entrada:{tamanho, media, desvio, ...}}).');
  process.exit(1);
}
if (existsSync(join(origem, 'reciclathe.onnx.data'))) {
  console.warn('Aviso: há um reciclathe.onnx.data (pesos externos). Exporte o modelo com os pesos embutidos.');
}

for (const nome of arquivos) {
  copyFileSync(join(origem, nome), join(destino, nome));
  console.log(`Copiado: ${nome} → assets/modelo/`);
}

writeFileSync(
  registro,
  `/**
 * Registro do modelo empacotado no app.
 *
 * GERADO por \`npm run copiar-modelo\` em ${new Date().toISOString()}.
 * Modelo: ${rotulos.versao ?? '(sem versão)'} — classes: ${rotulos.classes.join(', ')}.
 * Para voltar ao modo desenvolvimento, restaure este arquivo com os valores null
 * (veja o README) ou rode \`npm run remover-modelo\`.
 */

/** Módulo de asset do reciclathe.onnx (número devolvido pelo require do Metro) ou null. */
export const MODULO_MODELO: number | null = require('../../assets/modelo/reciclathe.onnx');

/** Conteúdo do labels.json ou null. */
export const ROTULOS_JSON: unknown = require('../../assets/modelo/labels.json');
`,
);
console.log('Registrado em src/classificacao/modeloRegistrado.ts. Reinicie o Metro com: npx expo start --clear');
