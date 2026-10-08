# ReciclaTHE — app móvel

Aplicativo do TCC **ReciclaTHE**: orienta o descarte correto de resíduos em Teresina-PI.
O usuário fotografa o resíduo, o app identifica o material **offline, no próprio aparelho**
(MobileNetV3 em ONNX), mostra a cor da lixeira (CONAMA 275/2001), como preparar e onde
descartar, o Disk Coleta da zona e os pontos de entrega no mapa.

- Expo SDK 57 · React Native 0.86 · React 19.2 · TypeScript estrito · Expo Router
- Classificação: `onnxruntime-react-native` (precisa de **development build**)
- Mapa: `@maplibre/maplibre-react-native` + tiles raster do OpenStreetMap (sem chave de API)
- Cache/sessão: AsyncStorage · Cliente HTTP: `fetch` com um wrapper fino

## Estrutura

```text
app/
├── src/
│   ├── app/                     # rotas (Expo Router)
│   │   ├── _layout.tsx          # providers (sessão, dados) + Stack
│   │   ├── index.tsx            # redireciona p/ /entrar ou /identificar
│   │   ├── entrar.tsx · cadastro.tsx · privacidade.tsx
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
├── assets/modelo/               # reciclathe.onnx + labels.json (copiados de ../ml/modelo)
├── scripts/copiar-modelo.mjs    # copia e registra o modelo
└── __tests__/                   # jest-expo: pré-processamento, cliente da API, fila offline
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

> HTTP sem TLS: `app.json` liga `usesCleartextTraffic` (via `expo-build-properties`) para o
> app falar com a API local em `http://`. Em produção, use HTTPS e remova essa opção.

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
para `assets/modelo/` e reescreve `src/classificacao/modeloRegistrado.ts` com os `require(...)`
(o Metro não aceita `require` condicional de arquivo inexistente). `npm run remover-modelo`
desfaz. O `metro.config.js` adiciona `.onnx` às extensões de asset.

Pipeline (`src/classificacao/classificadorOnnx.ts`):

1. redimensionamento da foto inteira para `entrada.tamanho` × `entrada.tamanho` (224×224, sem
   recorte e sem preservar a proporção) com `expo-image-manipulator` → JPEG;
2. bytes do arquivo (`expo-file-system`) → pixels RGBA com `jpeg-js`;
3. `Float32Array` NCHW `[1,3,224,224]` com `(pixel/255 − média)/desvio` por canal;
4. sessão ONNX: entrada `input`, saída `logits` `[1,7]` → softmax → top-3.

Se `confiança < 0,6`, o app mostra as 3 classes mais prováveis para o usuário escolher.
A previsão registrada na API é sempre o top-1 do modelo; a escolha do usuário vira o feedback.

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
| Ponto | Endereço, horário, materiais, fonte, "Como chegar" (app de mapas do aparelho), check-in (+5). |
| Sugerir ponto | Formulário, posição pelo mapa ou GPS, materiais, foto opcional; vai para aprovação (+20 se aprovada). |
| Perfil | Pontos, histórico, ranking geral e da zona, trocar/detectar zona, sair, excluir conta. |
| Como ganhar pontos | Regras: sugestão aprovada 20, check-in 5, feedback 2 (até 5/dia). Fotografar não pontua. |

### Offline e sincronização

- Ao abrir o app, quando a conexão volta (NetInfo) e no pull-to-refresh: baixa
  `GET /materiais`, `/zonas` e `/pontos` (sem lat/lon = todos os aprovados) para o AsyncStorage.
- Identificações e feedbacks feitos sem conexão entram numa **fila** (`src/armazenamento/fila.ts`).
  O feedback de uma identificação ainda não enviada referencia o id local; ao sincronizar, a
  identificação é enviada primeiro e o id do servidor é usado no feedback. Erros 4xx definitivos
  descartam o item; erro de rede/5xx/401 interrompe e mantém o restante.
- Fotos de feedback com consentimento enfileiradas são copiadas para a pasta de documentos e
  apagadas depois do envio.
- Ao sair da conta a fila é limpa (os itens pertencem ao usuário que saiu).
- A localização de zona usa `GET /zonas/localizar`; o 404 (bairros não importados) é tratado
  pedindo que o usuário escolha a zona.

## Verificações

```bash
npm run typecheck                      # tsc --noEmit (strict)
npm test                               # jest-expo
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
  "© OpenStreetMap contributors" aparece no mapa (licença ODbL).
  Observação: os servidores `tile.openstreetmap.org` seguem a
  [política de uso](https://operations.osmfoundation.org/policies/tiles/) — adequada para o TCC
  e testes; para uso em produção com muitos usuários, troque a URL em
  `src/componentes/MapaBase.tsx` por um servidor de tiles próprio ou de um provedor.
- **expo-image-picker** (câmera/galeria) em vez de `expo-camera`: menos código, UI nativa do
  sistema e funciona no Expo Go.
- **expo-image-manipulator + jpeg-js**: redimensiona com código nativo e decodifica o JPEG
  224×224 em JS puro (rápido nesse tamanho), sem módulo nativo extra.
- **AsyncStorage** para token, perfil, cache e fila (pedido do projeto). Para produção, o token
  poderia ir para `expo-secure-store`.
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
