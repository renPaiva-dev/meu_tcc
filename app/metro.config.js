// Configuração do Metro: inclui .onnx como asset para empacotar o modelo de classificação.
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);
config.resolver.assetExts.push('onnx');

module.exports = config;
