# ReciclaTHE

Aplicativo de orientação de descarte de resíduos para **Teresina-PI**: o cidadão fotografa um item, o celular identifica o material **offline** e o app explica como descartar e mostra onde entregar na cidade.

TCC no formato relatório técnico de software.

| Pasta | Conteúdo |
|---|---|
| [api/](api/) | API REST + painel administrativo (Spring Boot, PostgreSQL/PostGIS) |
| [app/](app/) | App móvel (React Native / Expo) |
| [ml/](ml/) | Pipeline de treino e avaliação do classificador (PyTorch → ONNX) |

Para subir o banco: `docker compose up -d db`. Instruções de cada parte no README da respectiva pasta.
