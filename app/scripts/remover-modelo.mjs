#!/usr/bin/env node
/** Desfaz o `copiar-modelo`: apaga os assets e o registro local (volta ao modo desenvolvimento). */
import { rmSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';

const require = createRequire(import.meta.url);
const { pastaAssets, registroLocal, ARQUIVOS_MODELO } = require('./modelo-local.js');

for (const nome of ARQUIVOS_MODELO) rmSync(join(pastaAssets, nome), { force: true });
rmSync(registroLocal, { force: true });
console.log('Modelo removido; o app volta a usar o classificador de desenvolvimento (reinicie o Metro com --clear).');
