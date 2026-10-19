# ReciclaTHE — app móvel

Aplicativo do TCC **ReciclaTHE**: orienta o descarte correto de resíduos em Teresina-PI.
O usuário fotografa o resíduo, o app identifica o material **offline, no próprio aparelho**
(MobileNetV3 em ONNX), mostra a cor da lixeira (CONAMA 275/2001), como preparar e onde
descartar, o Disk Coleta da zona e os pontos de entrega no mapa.

- Expo SDK 57 · React Native 0.86 · React 19.2 · TypeScript estrito · Expo Router
- Classificação: `onnxruntime-react-native` (precisa de **development build**)
- Mapa: `@maplibre/maplibre-react-native` + tiles raster do OpenStreetMap (sem chave de API)
- Token JWT: `expo-secure-store` · Cache, perfil e fila offline: AsyncStorage · Cliente HTTP: `fetch` com um wrapper fino

## Estrutura

```text
app/
├── src/
│   ├── app/                     # rotas (Expo Router)
│   │   ├── _layout.tsx          # providers, Stack (telas logadas em Stack.Protected) e ErrorBoundary
│   │   ├── index.tsx            # redireciona p/ /entrar ou /identificar
│   │   ├── entrar.tsx · cadastro.tsx · privacidade.tsx · creditos.tsx
│   │   ├── (abas)/              # Tabs: identificar · mapa · perfil
│   │   ├── resultado.tsx        # material, cor, confiança, orientação, feedback
│   │   ├── ponto/[id].tsx       # detalhe do ponto, "Como chegar", check-in
│   │   ├── sugerir.tsx          # sugestão de ponto (multipart, vai para aprovação)
│   │   └── como-ganhar-pontos.tsx
│   ├── api/                     # cliente HTTP (ErroApi/ErroRede), tipos do contrato, instância
│   ├── classificacao/           # interface Classificador, ONNX, mock de desenvolvimento, pré-processamento
│   ├── armazenamento/           # AsyncStorage, cache offline, fila offline
│   ├── sincronizacao/           # download do cache + envio da fila
│   ├── contexto/                # SessaoContexto, DadosContexto, identificação em andamento
│   ├── componentes/             # botões, campos, chips, mapa (MapLibre), tema
│   └── utilitarios/             # geo (haversine), localização, fotos, materiais
├── assets/modelo/               # reciclathe.onnx + labels.json (copiados de ../ml/modelo; fora do git)
├── scripts/                     # copiar/remover/verificar-modelo + modelo-local.js (usado pelo Metro)
├── eas.json                     # perfis development / preview / production
└── __tests__/                   # jest-expo: pré-processamento, cliente, fila, token, utilitários, registro do modelo
```

## Como rodar

Pré-requisitos: Node 20+ (testado com Node 24 / npm 11), Android Studio com SDK e um
emulador ou aparelho com depuração USB, JDK 17.

```bash
cd app
npm install

# 1) Endereço da API (opcional; padrão http://10.0.2.2:8080 = PC visto do emulador Android)
cp .env.example .env
#   aparelho físico: EXPO_PUBLIC_API_URL=http://<IP-do-PC-na-rede>:8080

# 2) Modelo de classificação (quando ml/modelo/ já tiver sido gerado)
npm run copiar-modelo

# 3) Development build + Metro
npx expo run:android          # compila o app nativo (inclui ONNX Runtime e MapLibre) e instala
# depois, nas próximas vezes, basta:
npx expo start --dev-client
```

Com EAS (build na nuvem, opcional — exige conta Expo, que este projeto não cria):
`npx eas-cli@latest build --profile development --platform android`.

`EXPO_PUBLIC_*` é embutida no bundle no momento do `expo start`/`export`; depois de mudar
o `.env`, reinicie o Metro com `npx expo start --clear`.

> HTTP sem TLS **só para desenvolvimento**: `app.json` liga `usesCleartextTraffic` no Android (via
> `expo-build-properties`) e `NSAllowsLocalNetworking` no iOS (ATS) para o app falar com a API local
> em `http://`. Em produção a API **deve** usar HTTPS (`EXPO_PUBLIC_API_URL=https://...`, como no perfil
> `production` do `eas.json`) e essas exceções devem ser removidas do `app.json`.

### Builds com EAS (`eas.json`)

| Perfil | Uso | API |
| --- | --- | --- |
| `development` | development build (expo-dev-client), distribuição interna | `http://10.0.2.2:8080` |
| `preview` | APK para testes/banca | `https://api-homologacao.reciclathe.example` (troque) |
| `production` | AAB para a loja | `https://api.reciclathe.example` (troque) |

As URLs `*.example` são marcadores: substitua pelo endereço real antes de gerar o build.
`android.versionCode` e `ios.buildNumber` ficam no `app.json` (`appVersionSource: local`); aumente-os a
cada envio para as lojas. A splash screen é configurada pelo plugin `expo-splash-screen`
(`assets/splash-icon.png`); o app é só retrato (`supportsTablet: false`).

### Expo Go (limitado)

O Expo Go **não** contém os módulos nativos do ONNX Runtime nem do MapLibre. O app detecta
isso e continua utilizável para testar as telas:

- a classificação usa o **classificador de desenvolvimento** (resultados **aleatórios**), com um
  aviso amarelo bem visível nas telas Identificar e Resultado;
- a aba Mapa mostra apenas a **lista** de pontos (ordenada por distância), sem o mapa.

O mesmo classificador de desenvolvimento é usado num development build enquanto o modelo
não for copiado (`npm run copiar-modelo`).

## Modelo (ONNX)

`npm run copiar-modelo [-- <pasta>]` copia `reciclathe.onnx` e `labels.json` de `../ml/modelo/`
para `assets/modelo/` e gera `src/classificacao/modeloRegistrado.local.ts` com os `require(...)`.
Os assets e esse arquivo **não vão para o git**. O `src/classificacao/modeloRegistrado.ts` versionado
é sempre o stub com `null`; o `metro.config.js` só troca o stub pelo arquivo local quando ele **e**
os arquivos que ele referencia existem (o Metro não aceita `require` condicional de arquivo
inexistente). Assim um clone novo sempre compila (com o classificador de desenvolvimento).
`npm run remover-modelo` desfaz; `npm run verificar-modelo` (e o teste `__tests__/modelo.test.ts`)
falha se o stub versionado tiver `require` ou se o registro local apontar para arquivo inexistente.
Se `../ml/modelo/reciclathe.onnx` não existir, o `copiar-modelo` explica que ele precisa ser gerado
no projeto `ml/`. Depois de copiar/remover, reinicie o Metro com `npx expo start --clear`.
O `metro.config.js` também adiciona `.onnx` às extensões de asset.

Pipeline (`src/classificacao/classificadorOnnx.ts`):

1. redimensionamento da foto inteira para `entrada.tamanho` × `entrada.tamanho` (224×224, sem
   recorte e sem preservar a proporção) com `expo-image-manipulator` → JPEG;
2. bytes do arquivo (`expo-file-system`) → pixels RGBA com `jpeg-js`;
3. `Float32Array` NCHW `[1,3,224,224]` com `(pixel/255 − média)/desvio` por canal;
4. sessão ONNX: entrada `input`, saída `logits` `[1,7]` → softmax → top-3.

Se `confiança < 0,6`, o app mostra as 3 classes mais prováveis para o usuário escolher.
A previsão registrada na API é sempre o top-1 do modelo; a escolha do usuário vira o feedback.
No feedback "Não", o material previsto não aparece entre as opções de correção.
Resultados do **classificador de desenvolvimento** (`mock-aleatorio`) **não são registrados** na API
nem aceitam feedback (aviso amarelo na tela).

> O pré-processamento precisa ser idêntico ao da avaliação do modelo: `T.Resize((224, 224))` em
> `ml/src/comum.py`. Se o treino mudar (ex.: `Resize(256)+CenterCrop(224)`), ajuste
> `carregarPixels()` para ficar igual.

## Funcionalidades

| Tela | O que faz |
| --- | --- |
| Entrar / Criar conta | Login, cadastro com zona de Teresina e aviso de privacidade (LGPD). |
| Identificar | Foto (câmera ou galeria) → classificação offline → Resultado. Pull-to-refresh sincroniza. |
| Resultado | Material, cor CONAMA, confiança, top-3 se incerto, orientação do cache, Disk Coleta (`tel:`), "Onde entregar" (mapa filtrado), "A identificação está certa?" com consentimento da foto **desmarcado por padrão**. |
| Mapa | Pontos aprovados do cache, filtro por material, localização do usuário, lista por distância. |
| Ponto | Endereço, horário, materiais, fonte, "Como chegar" (app de mapas do aparelho), check-in (+5). Fora do cache, busca `GET /pontos/{id}`. Pontos do OSM mostram "© colaboradores do OpenStreetMap". |
| Sugerir ponto | Formulário, posição pelo mapa ou GPS, materiais, foto opcional; vai para aprovação (+20 se aprovada). |
| Perfil | Pontos, histórico, ranking geral e da zona, trocar/detectar zona, sair, excluir conta. |
| Como ganhar pontos | Regras: sugestão aprovada 20, check-in 5, feedback 2 (até 5/dia). Fotografar não pontua. |
| Créditos e licenças | Datasets (TrashNet, RealWaste, TACO, GIZ), MobileNetV3/torchvision, OpenStreetMap (ODbL), bibliotecas. Link no Perfil e em Privacidade. |

Telas que exigem sessão (abas, resultado, ponto, sugerir, como ganhar pontos) ficam dentro de
`Stack.Protected`: quando a sessão termina, o Router volta ao início e de lá para `/entrar`.
O layout raiz exporta um `ErrorBoundary` com botão "Tentar novamente".

### Offline e sincronização

- Ao abrir o app, quando a conexão volta (NetInfo) e no pull-to-refresh: baixa
  `GET /materiais`, `/zonas` e `/pontos` (sem lat/lon = todos os aprovados) para o AsyncStorage.
- Identificações e feedbacks que falham por rede, tempo esgotado, 5xx ou 401/408/429 — inclusive na
  **primeira** tentativa — entram numa **fila** (`src/armazenamento/fila.ts`). Uma resposta 200 que não
  é JSON (portal cativo de Wi-Fi) conta como falha de rede. Erros 4xx definitivos não são enfileirados.
- O feedback de uma identificação ainda não enviada referencia o id local (UUID gerado no app);
  ao sincronizar, a identificação é enviada primeiro e o id do servidor é usado no feedback. O mapa
  id local → id do servidor é mantido por 30 dias (até 500 itens), então um feedback dado **depois**
  da sincronização (tela de resultado ainda aberta) também é entregue. Se a identificação não existir
  mais, o app mostra erro em vez de prometer o envio.
- O progresso da fila é gravado a cada item; todas as operações na fila (inclusive limpar) passam por
  uma fila serial, e o envio para se a sessão mudar no meio.
- Limitação: a API não tem chave de idempotência, então um tempo esgotado em que o servidor chegou a
  gravar pode gerar uma identificação duplicada ao reenviar; o app só evita duplicar o item na fila local.
- A fila guarda o **id do usuário dono**. Token expirado (401, após 30 dias): a sessão termina com o
  aviso "Sua sessão expirou, entre novamente" e a fila é **mantida**; ela é enviada se o mesmo usuário
  entrar de novo e descartada se outro usuário entrar. **Sair** (explícito) e **excluir conta** apagam
  a fila e as fotos dela.
- Fotos de feedback com consentimento enfileiradas são copiadas para `fila-fotos/` (documentos) e
  apagadas depois do envio ou ao sair; as cópias reduzidas geradas para envio (cache) são apagadas
  logo após o envio.
- A localização de zona usa `GET /zonas/localizar`; o 404 (bairros não importados) é tratado
  pedindo que o usuário escolha a zona.

## Verificações

```bash
npm run typecheck                      # tsc --noEmit (strict)
npm run lint                           # expo lint (ESLint 9 + eslint-config-expo, eslint.config.js)
npm test                               # jest-expo
npm run verificar-modelo               # registro do modelo não aponta para arquivo ausente
npx expo-doctor
npx expo export --platform android     # garante que o bundle JS compila
```

## Escolha das bibliotecas

- **Expo + Expo Router**: base recomendada para React Native; Continuous Native Generation
  (`android/` e `ios/` são gerados por `expo prebuild`/`run:android` a partir do `app.json`).
- **onnxruntime-react-native**: roda o mesmo `.onnx` exportado pelo PyTorch, sem converter para
  TFLite; tem config plugin para Expo. Exige development build.
- **MapLibre Native (`@maplibre/maplibre-react-native`)** em vez de `react-native-maps`: no
  Android o `react-native-maps` usa o Google Maps SDK, que **exige chave de API** do Google Cloud
  (com faturamento ativado) mesmo usando `UrlTile` do OSM. O MapLibre é open source, não exige
  chave e aceita um estilo próprio com tiles raster do OpenStreetMap. A atribuição
  "© OpenStreetMap contributors" aparece no mapa (licença ODbL). Os servidores de tiles recebem o IP
  do aparelho (informado na tela de Privacidade).
  Observação: os servidores `tile.openstreetmap.org` seguem a
  [política de uso](https://operations.osmfoundation.org/policies/tiles/) — adequada para o TCC
  e testes; para uso em produção com muitos usuários, troque a URL em
  `src/componentes/MapaBase.tsx` por um servidor de tiles próprio ou de um provedor.
- **expo-image-picker** (câmera/galeria) em vez de `expo-camera`: menos código, UI nativa do
  sistema e funciona no Expo Go.
- **expo-image-manipulator + jpeg-js**: redimensiona com código nativo e decodifica o JPEG
  224×224 em JS puro (rápido nesse tamanho), sem módulo nativo extra.
- **expo-secure-store** para o token JWT (Keystore/Keychain); um token antigo salvo no AsyncStorage é
  migrado na primeira abertura. **AsyncStorage** para perfil, cache e fila.
- **@react-native-community/netinfo**: dispara a sincronização quando a internet volta.

## Patch do onnxruntime-react-native

O `android/build.gradle` do `onnxruntime-react-native@1.24.3` usa `org.gradle.util.VersionNumber`,
que foi removido no Gradle 9 (usado pelo Expo SDK 57), e o build Android falha com
`Could not get unknown property 'VersionNumber'`. O arquivo `patches/onnxruntime-react-native+1.24.3.patch`
troca essa comparação por `REACT_NATIVE_MINOR_VERSION < 71` e é aplicado automaticamente pelo
`patch-package` no `postinstall`. Por isso a versão do pacote está fixada em `1.24.3`; ao atualizá-lo,
confira se o patch ainda é necessário.

O mesmo patch **remove o `unimodule.json`** do pacote. Esse arquivo antigo faz o autolinking do Expo
tratar o pacote como "módulo Expo": ele é compilado, mas o `OnnxruntimePackage` nunca entra no
`PackageList` do React Native, então `NativeModules.Onnxruntime` fica indefinido e o app cai, sem
erro visível, no classificador aleatório (`versao_modelo = mock-aleatorio` na API). Para conferir
depois de um build: `PackageList.java` (em `android/app/build/generated/autolinking/`) deve conter
`new ai.onnxruntime.reactnative.OnnxruntimePackage()`.

## Limitações conhecidas

- ONNX Runtime e MapLibre não funcionam no Expo Go (o app cai no mock / lista, com aviso).
- Os tiles do mapa precisam de internet; offline, os pontos continuam na lista e no detalhe.
- O `NativeModules.Onnxruntime` é consultado antes de importar o pacote, porque o pacote chama
  o módulo nativo já no `import`.
- A tela de sugestão coloca o mapa dentro de uma rolagem; em alguns aparelhos é preciso
  arrastar o mapa com cuidado (o toque para marcar funciona normalmente).
