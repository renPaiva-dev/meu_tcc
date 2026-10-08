# ReciclaTHE — pipeline de treino do classificador de resíduos

Classificador de imagens (MobileNetV3-Large, transfer learning) que prevê uma das 7
classes usadas pela API e pelo app, **nesta ordem**:

`PLASTICO, PAPEL, METAL, VIDRO, ELETRONICO, ORGANICO, REJEITO`

Somente datasets públicos com licença explícita (ver [DATASETS.md](DATASETS.md)).
Resultados e discussão: [RESULTADOS.md](RESULTADOS.md).

## Estrutura

```
ml/
├── src/
│   ├── baixar_dados.py          # baixa os datasets para dados/brutos/<nome>/
│   ├── mapeamento.py            # classes de origem -> 7 classes ReciclaTHE
│   ├── preparar_dados.py        # recortes, dedup, resize, split 70/15/15 por dataset, manifest
│   ├── comum.py                 # dataset, transforms, modelo, métricas (compartilhado)
│   ├── treinar.py               # treino com early stopping em F1-macro de validação
│   ├── experimento_dominio.py   # experimento de generalização entre datasets (domain shift)
│   ├── exportar_onnx.py         # ONNX opset 17 + labels.json + versões INT8
│   └── avaliar.py               # métricas no teste, matriz de confusão, latência CPU, tamanho
├── dados/                       # (ignorado no git) brutos/, processados/, manifest.csv
├── modelo/                      # reciclathe.onnx, reciclathe-int8*.onnx, labels.json, checkpoints/
├── resultados/                  # JSON/PNG/MD pequenos com os resultados
├── requirements.txt
├── DATASETS.md
└── RESULTADOS.md
```

## Como reproduzir

Pré-requisitos: Linux, Python 3.12, ~12 GB livres em disco, GPU NVIDIA opcional
(sem GPU o treino roda em CPU, bem mais devagar). Nenhuma credencial é necessária.

```bash
cd ml

# 1) ambiente (o python3-venv do sistema não estava disponível; usamos o uv)
uv venv --python python3.12 .venv
VIRTUAL_ENV=.venv uv pip install -r requirements.txt
#   alternativa sem uv:  python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

# 2) download (~6,3 GB: TrashNet 43 MB, RealWaste 690 MB, TACO 2,7 GB, GIZ 2,8 GB).
#    Retoma downloads interrompidos. Rode com -I (dados baixados não são confiáveis).
.venv/bin/python -I src/baixar_dados.py

# 3) preparo: recortes TACO/GIZ, dedup (MD5 + dHash), resize (lado menor 256), split, manifest
.venv/bin/python -I src/preparar_dados.py

# 4) modelo principal (todos os datasets, 7 classes)
.venv/bin/python src/treinar.py --nome combinado --epocas 25

# 5) experimento de domain shift (treina so_trashnet, so_realwaste, sem_taco e avalia)
.venv/bin/python src/experimento_dominio.py --epocas 25
#    (--so-avaliar reaproveita checkpoints já treinados)

# 6) exportação ONNX (+ INT8 dinâmica e estática) e labels.json
.venv/bin/python src/exportar_onnx.py --checkpoint modelo/checkpoints/combinado.pt

# 7) avaliação no conjunto de teste (ONNX fp32 e INT8) + latência CPU
.venv/bin/python src/avaliar.py
```

Seed fixa (42) em split, embaralhamento e inicialização. Em GPU, `cudnn.benchmark`
introduz pequena não-determinância numérica (resultados podem variar na 3ª casa decimal).

## Contrato do modelo (para a API)

`modelo/labels.json` descreve o pré-processamento esperado:

1. converter a imagem para RGB e redimensionar **direto** para 224×224 (sem recorte);
2. `x = pixel / 255`, normalizar com média `[0.485, 0.456, 0.406]` e desvio `[0.229, 0.224, 0.225]`;
3. tensor `float32` no layout NCHW `[1, 3, 224, 224]`, entrada `"input"`;
4. saída `"logits"` `[1, 7]` → aplicar softmax; índice *i* corresponde a `classes[i]`.

`avaliar.py` implementa exatamente esse pré-processamento em PIL + NumPy (função
`preprocessar`), servindo de referência para a implementação na API.
