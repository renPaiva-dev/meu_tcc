"""Avalia os modelos ONNX exportados no conjunto de TESTE.

Para reciclathe.onnx (fp32) e reciclathe-int8.onnx (se existir):
  - acurácia, F1-macro, precisão/revocação/F1 por classe (teste total e por dataset)
  - matriz de confusão (PNG, contagens e normalizada por linha)
  - tempo médio de inferência em CPU, batch 1 (onnxruntime), e tamanho do arquivo

O pré-processamento aqui é implementado com PIL + NumPy exatamente como
descrito em modelo/labels.json (o mesmo que a API/app devem fazer).

Uso:
    .venv/bin/python src/avaliar.py
"""
from __future__ import annotations

import json
import platform
import statistics
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import comum as C  # noqa: E402


def preprocessar(caminho: Path, cfg: dict) -> np.ndarray:
    t = cfg["tamanho"]
    with Image.open(caminho) as im:
        im = im.convert("RGB").resize((t, t), Image.BILINEAR)
    x = np.asarray(im, dtype=np.float32) / 255.0
    x = (x - np.array(cfg["media"], dtype=np.float32)) / np.array(cfg["desvio"], dtype=np.float32)
    return x.transpose(2, 0, 1)[None]  # NCHW


def sessao(caminho: Path, threads: int | None = None):
    import onnxruntime as ort
    so = ort.SessionOptions()
    if threads:
        so.intra_op_num_threads = threads
        so.inter_op_num_threads = 1
    return ort.InferenceSession(str(caminho), so, providers=["CPUExecutionProvider"])


def medir_latencia(caminho: Path, threads: int | None, n: int = 200) -> dict:
    s = sessao(caminho, threads)
    rng = np.random.default_rng(C.SEED)
    x = rng.standard_normal((1, 3, 224, 224)).astype(np.float32)
    for _ in range(20):  # aquecimento
        s.run(["logits"], {"input": x})
    tempos = []
    for _ in range(n):
        t0 = time.perf_counter()
        s.run(["logits"], {"input": x})
        tempos.append((time.perf_counter() - t0) * 1000)
    tempos.sort()
    return {"threads": threads or "padrão ORT", "media_ms": statistics.mean(tempos),
            "mediana_ms": statistics.median(tempos), "p95_ms": tempos[int(0.95 * n) - 1], "n": n}


def plotar_matriz(m: dict, destino: Path, titulo: str) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    cm = np.array(m["matriz_confusao"], dtype=float)
    classes = m["classes"]
    norm = cm / np.maximum(cm.sum(1, keepdims=True), 1)
    fig, ax = plt.subplots(figsize=(7.2, 6.2), dpi=150)
    ax.imshow(norm, cmap="Blues", vmin=0, vmax=1)
    for i in range(len(classes)):
        for j in range(len(classes)):
            if cm[i, j] == 0:
                continue
            cor = "white" if norm[i, j] > 0.55 else "#1f2328"
            ax.text(j, i, f"{int(cm[i, j])}\n{norm[i, j]:.0%}", ha="center", va="center", fontsize=7.5, color=cor)
    ax.set_xticks(range(len(classes)), classes, rotation=40, ha="right", fontsize=8)
    ax.set_yticks(range(len(classes)), classes, fontsize=8)
    ax.set_xlabel("Classe predita")
    ax.set_ylabel("Classe verdadeira")
    ax.set_title(titulo, fontsize=10)
    for s in ax.spines.values():
        s.set_visible(False)
    fig.tight_layout()
    fig.savefig(destino)
    plt.close(fig)


def avaliar_onnx(caminho: Path, cfg: dict, classes: list[str], linhas: list[dict]) -> dict:
    s = sessao(caminho)
    idx = {c: i for i, c in enumerate(classes)}
    y, p, ds = [], [], []
    for l in linhas:
        out = s.run(["logits"], {"input": preprocessar(C.RAIZ / l["caminho"], cfg)})[0]
        y.append(idx[l["classe"]])
        p.append(int(out.argmax()))
        ds.append(l["dataset_origem"])
    y, p, ds = np.array(y), np.array(p), np.array(ds)
    res = {"geral": C.metricas(y, p, classes)}
    for d in sorted(set(ds)):
        res[d] = C.metricas(y[ds == d], p[ds == d], classes)
    return res


def main():
    cfg_all = json.loads((C.MODELO_DIR / "labels.json").read_text())
    cfg, classes = cfg_all["entrada"], cfg_all["classes"]
    teste = C.ler_manifest(split="teste")
    print(f"teste: {len(teste)} imagens")
    saida = {"plataforma": {"cpu": platform.processor() or platform.machine(), "python": platform.python_version()}}
    try:
        import onnxruntime as ort
        saida["plataforma"]["onnxruntime"] = ort.__version__
        with open("/proc/cpuinfo") as f:
            nome = next((l.split(":", 1)[1].strip() for l in f if l.startswith("model name")), None)
        saida["plataforma"]["cpu"] = nome or saida["plataforma"]["cpu"]
    except Exception:
        pass

    for rotulo, arq in [("fp32", "reciclathe.onnx"), ("int8", "reciclathe-int8.onnx"),
                         ("int8_estatico", "reciclathe-int8-estatico.onnx")]:
        caminho = C.MODELO_DIR / arq
        if not caminho.exists():
            continue
        print(f"[{rotulo}] avaliando {arq} ...")
        r = avaliar_onnx(caminho, cfg, classes, teste)
        r["arquivo"] = arq
        r["tamanho_mb"] = caminho.stat().st_size / 1e6
        r["latencia_cpu_batch1"] = [medir_latencia(caminho, 1), medir_latencia(caminho, 4), medir_latencia(caminho, None)]
        saida[rotulo] = r
        g = r["geral"]
        print(f"  acc={g['acuracia']:.4f} f1_macro={g['f1_macro']:.4f} tamanho={r['tamanho_mb']:.2f} MB "
              f"lat1t={r['latencia_cpu_batch1'][0]['media_ms']:.2f} ms")
        for c, v in g["por_classe"].items():
            print(f"    {c:11s} P={v['precisao']:.3f} R={v['revocacao']:.3f} F1={v['f1']:.3f} n={v['suporte']}")
        plotar_matriz(g, C.RESULT / f"matriz_confusao_{rotulo}.png",
                      f"Matriz de confusão — teste (n={g['n']}) — {arq}")
    (C.RESULT / "avaliacao_teste.json").write_text(json.dumps(saida, indent=2, ensure_ascii=False))
    print("salvo em resultados/avaliacao_teste.json")


if __name__ == "__main__":
    main()
