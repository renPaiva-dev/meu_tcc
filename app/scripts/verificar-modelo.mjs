#!/usr/bin/env node
/** Falha se o registro do modelo referenciar arquivos inexistentes (o bundle do Metro quebraria). */
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { verificarRegistro, modeloLocalDisponivel } = require('./modelo-local.js');

const problemas = verificarRegistro();
if (problemas.length > 0) {
  for (const p of problemas) console.error(`✗ ${p}`);
  process.exit(1);
}
console.log(
  modeloLocalDisponivel()
    ? '✓ Modelo registrado (modeloRegistrado.local.ts) e arquivos presentes.'
    : '✓ Sem modelo local: o app usa o classificador de desenvolvimento.',
);
