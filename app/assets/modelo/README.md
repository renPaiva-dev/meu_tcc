# Modelo de classificação (assets/modelo)

Esta pasta recebe os arquivos gerados pelo projeto de ML (`../ml/modelo/`):

| Arquivo | Conteúdo |
| --- | --- |
| `reciclathe.onnx` | MobileNetV3 exportado para ONNX. Entrada `input` float32 `[1,3,224,224]`, saída `logits` `[1,7]`. |
| `labels.json` | `{"versao", "classes", "entrada": {"tamanho", "media", "desvio", "layout": "NCHW", "escala": "pixel/255"}}` |

Eles **não** são versionados aqui. Para copiá-los e registrá-los no app:

```bash
npm run copiar-modelo            # usa ../ml/modelo
npm run copiar-modelo -- /outra/pasta
npx expo start --clear           # o Metro precisa reempacotar o asset
```

O script também gera `src/classificacao/modeloRegistrado.local.ts` (ignorado pelo git) com os `require(...)`
dos arquivos (o Metro não permite `require` condicional de arquivo inexistente).
`npm run remover-modelo` desfaz isso.

Sem o modelo, o app usa o **classificador de desenvolvimento**, que devolve resultados
**aleatórios** e mostra um aviso na tela. O mesmo acontece no Expo Go, que não tem o módulo
nativo do ONNX Runtime.

Observação: exporte o modelo com os pesos embutidos no `.onnx` (sem arquivo `.onnx.data` externo).
