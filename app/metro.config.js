// Configuração do Metro: inclui .onnx como asset para empacotar o modelo de classificação.
const { getDefaultConfig } = require('expo/metro-config');
const path = require('node:path');

const { modeloLocalDisponivel, registroLocal } = require('./scripts/modelo-local');

const config = getDefaultConfig(__dirname);
config.resolver.assetExts.push('onnx');

// Usa o registro gerado por `npm run copiar-modelo` (ignorado pelo git) quando ele e os
// arquivos do modelo existem; senão fica o stub versionado (classificador de desenvolvimento).
if (modeloLocalDisponivel()) {
  const resolverPadrao = config.resolver.resolveRequest;
  config.resolver.resolveRequest = (contexto, nomeModulo, plataforma) => {
    if (nomeModulo === './modeloRegistrado' && path.dirname(contexto.originModulePath) === path.dirname(registroLocal)) {
      return { type: 'sourceFile', filePath: registroLocal };
    }
    return (resolverPadrao ?? contexto.resolveRequest)(contexto, nomeModulo, plataforma);
  };
}

module.exports = config;
