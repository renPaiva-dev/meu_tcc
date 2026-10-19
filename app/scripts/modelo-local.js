/**
 * Caminhos e verificações do modelo empacotado, compartilhados pelo metro.config.js,
 * pelos scripts copiar/remover/verificar-modelo e pelo teste __tests__/modelo.test.ts.
 *
 * O registro versionado (src/classificacao/modeloRegistrado.ts) é sempre o stub com null.
 * O `npm run copiar-modelo` gera modeloRegistrado.local.ts (ignorado pelo git) com os
 * require(...) dos arquivos; o Metro só usa esse arquivo se ele E os assets existirem.
 */
const { existsSync, readFileSync } = require('node:fs');
const { dirname, join, resolve } = require('node:path');

const raizApp = resolve(__dirname, '..');
const pastaClassificacao = join(raizApp, 'src', 'classificacao');
const registroVersionado = join(pastaClassificacao, 'modeloRegistrado.ts');
const registroLocal = join(pastaClassificacao, 'modeloRegistrado.local.ts');
const pastaAssets = join(raizApp, 'assets', 'modelo');
const ARQUIVOS_MODELO = ['reciclathe.onnx', 'labels.json'];

/** Caminhos (absolutos) de todos os require('...') relativos de um arquivo de registro. */
function requiresDoArquivo(arquivo) {
  const texto = readFileSync(arquivo, 'utf8');
  return [...texto.matchAll(/require\(\s*['"]([^'"]+)['"]\s*\)/g)].map((m) => resolve(dirname(arquivo), m[1]));
}

/** Problemas encontrados (lista vazia = tudo certo). Os caminhos podem ser trocados nos testes. */
function verificarRegistro(versionado = registroVersionado, local = registroLocal) {
  const problemas = [];
  if (requiresDoArquivo(versionado).length > 0) {
    problemas.push(
      'src/classificacao/modeloRegistrado.ts (versionado) não pode ter require(): os assets do modelo não ' +
        'estão no git e o bundle quebraria num clone novo. Rode `npm run remover-modelo`.',
    );
  }
  if (existsSync(local)) {
    for (const caminho of requiresDoArquivo(local)) {
      if (!existsSync(caminho)) {
        problemas.push(`modeloRegistrado.local.ts aponta para um arquivo inexistente: ${caminho}`);
      }
    }
  }
  return problemas;
}

/** true se o registro local pode ser usado (ele e todos os arquivos que ele referencia existem). */
function modeloLocalDisponivel() {
  if (!existsSync(registroLocal)) return false;
  return requiresDoArquivo(registroLocal).every((c) => existsSync(c));
}

module.exports = {
  raizApp,
  registroVersionado,
  registroLocal,
  pastaAssets,
  ARQUIVOS_MODELO,
  requiresDoArquivo,
  verificarRegistro,
  modeloLocalDisponivel,
};
