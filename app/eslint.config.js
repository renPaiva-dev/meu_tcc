// ESLint (flat config) com as regras recomendadas pelo Expo. Rode com `npm run lint`.
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/*', 'android/*', 'ios/*', '.expo/*'],
  },
  {
    // Scripts e configurações rodam no Node (CommonJS).
    files: ['scripts/**/*.js', '*.config.js'],
    languageOptions: { globals: { __dirname: 'readonly', require: 'readonly', module: 'writable' } },
  },
]);
