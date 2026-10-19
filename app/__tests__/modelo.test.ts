/**
 * O registro versionado do modelo não pode referenciar arquivos fora do git: num clone novo
 * o Metro falharia ao montar o bundle. Ver scripts/modelo-local.js.
 */
/* eslint-disable @typescript-eslint/no-require-imports */
// require tipado à mão: o tsconfig do app não inclui os tipos do Node.
const { mkdtempSync, writeFileSync } = require('node:fs') as {
  mkdtempSync: (prefixo: string) => string;
  writeFileSync: (arquivo: string, texto: string) => void;
};
const { tmpdir } = require('node:os') as { tmpdir: () => string };
const { join } = require('node:path') as { join: (...partes: string[]) => string };

const modeloLocal = require('../scripts/modelo-local.js') as {
  registroVersionado: string;
  verificarRegistro: (versionado?: string, local?: string) => string[];
  requiresDoArquivo: (arquivo: string) => string[];
};

describe('registro do modelo', () => {
  it('o stub versionado não tem require() e o registro local (se houver) aponta para arquivos existentes', () => {
    expect(modeloLocal.requiresDoArquivo(modeloLocal.registroVersionado)).toEqual([]);
    expect(modeloLocal.verificarRegistro()).toEqual([]);
  });

  it('detecta registro apontando para arquivo inexistente', () => {
    const pasta = mkdtempSync(join(tmpdir(), 'reciclathe-modelo-'));
    const comRequire = join(pasta, 'modeloRegistrado.ts');
    writeFileSync(comRequire, "export const MODULO_MODELO = require('./nao-existe.onnx');\n");
    const stub = join(pasta, 'stub.ts');
    writeFileSync(stub, 'export const MODULO_MODELO = null;\n');

    // Versionado com require → erro (mesmo que o arquivo exista na máquina de quem gerou).
    expect(modeloLocal.verificarRegistro(comRequire, join(pasta, 'sem-local.ts'))).toHaveLength(1);
    // Local apontando para arquivo que não existe → erro.
    const problemas = modeloLocal.verificarRegistro(stub, comRequire);
    expect(problemas).toHaveLength(1);
    expect(problemas[0]).toMatch(/nao-existe\.onnx/);
  });
});
