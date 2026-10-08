"""Treina o classificador (transfer learning MobileNetV3-Large, pesos ImageNet).

- Entrada 224x224, augmentations em comum.transform_treino()
- Balanceamento: CrossEntropy com pesos inversamente proporcionais à frequência
  da classe no treino (normalizados para média 1)
- Early stopping em F1-macro de validação; salva o melhor checkpoint

Exemplos:
    # modelo principal (todos os datasets, 7 classes)
    .venv/bin/python src/treinar.py --nome combinado
    # só TrashNet (experimento de domain shift)
    .venv/bin/python src/treinar.py --nome so_trashnet --datasets trashnet
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from collections import Counter

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader

sys.path.insert(0, str(__import__('pathlib').Path(__file__).resolve().parent))
import comum as C  # noqa: E402
from mapeamento import CLASSES  # noqa: E402


def avaliar_loader(modelo, loader, disp) -> tuple[np.ndarray, np.ndarray]:
    modelo.eval()
    ys, ps = [], []
    with torch.no_grad(), torch.autocast("cuda", enabled=disp.type == "cuda"):
        for x, y in loader:
            ps.append(modelo(x.to(disp, non_blocking=True)).argmax(1).cpu().numpy())
            ys.append(y.numpy())
    return np.concatenate(ys), np.concatenate(ps)


def treinar(nome: str, datasets: list[str] | None, epocas: int = 25, paciencia: int = 6,
            bs: int = 64, lr: float = 1e-3, wd: float = 1e-4, workers: int = 10) -> dict:
    C.fixar_seed()
    disp = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    treino = C.ler_manifest(datasets, "treino")
    val = C.ler_manifest(datasets, "val")
    # classes = as do contrato que existem no treino deste experimento, na ordem oficial
    presentes = {l["classe"] for l in treino}
    classes = [c for c in CLASSES if c in presentes]
    val = [l for l in val if l["classe"] in presentes]
    cont = Counter(l["classe"] for l in treino)
    print(f"[{nome}] datasets={datasets or 'todos'} classes={classes}")
    print(f"  treino={len(treino)} val={len(val)} dispositivo={disp} contagem={dict(cont)}")

    g = torch.Generator().manual_seed(C.SEED)
    dl_tr = DataLoader(C.DatasetManifest(treino, classes, C.transform_treino()), batch_size=bs, shuffle=True,
                       num_workers=workers, pin_memory=True, drop_last=True, generator=g,
                       persistent_workers=True)
    dl_val = DataLoader(C.DatasetManifest(val, classes, C.transform_avaliacao()), batch_size=128,
                        num_workers=workers, pin_memory=True, persistent_workers=True)

    freq = np.array([cont[c] for c in classes], dtype=np.float64)
    pesos = freq.sum() / (len(classes) * freq)
    pesos = pesos / pesos.mean()
    crit = nn.CrossEntropyLoss(weight=torch.tensor(pesos, dtype=torch.float32, device=disp), label_smoothing=0.1)

    modelo = C.criar_modelo(len(classes)).to(disp)
    cabeca = list(modelo.classifier.parameters())
    ids_cabeca = {id(p) for p in cabeca}
    corpo = [p for p in modelo.parameters() if id(p) not in ids_cabeca]
    # backbone com lr 10x menor que a cabeça (fine-tuning suave)
    opt = torch.optim.AdamW([{"params": corpo, "lr": lr / 10}, {"params": cabeca, "lr": lr}], weight_decay=wd)
    passos = epocas * len(dl_tr)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=[lr / 10, lr], total_steps=passos, pct_start=0.1)
    scaler = torch.amp.GradScaler("cuda", enabled=disp.type == "cuda")

    C.CKPT_DIR.mkdir(parents=True, exist_ok=True)
    caminho_ck = C.CKPT_DIR / f"{nome}.pt"
    historico, melhor, sem_melhora, t0 = [], -1.0, 0, time.time()
    for ep in range(1, epocas + 1):
        modelo.train()
        perda_tot, n = 0.0, 0
        for x, y in dl_tr:
            x, y = x.to(disp, non_blocking=True), y.to(disp, non_blocking=True)
            opt.zero_grad(set_to_none=True)
            with torch.autocast("cuda", enabled=disp.type == "cuda"):
                perda = crit(modelo(x), y)
            scaler.scale(perda).backward()
            scaler.step(opt)
            scaler.update()
            sched.step()
            perda_tot += perda.item() * len(y)
            n += len(y)
        yv, pv = avaliar_loader(modelo, dl_val, disp)
        mv = C.metricas(yv, pv, classes)
        historico.append({"epoca": ep, "perda_treino": perda_tot / n, "val_acuracia": mv["acuracia"],
                          "val_f1_macro": mv["f1_macro"], "segundos": round(time.time() - t0, 1)})
        print(f"  ep {ep:02d} perda={perda_tot/n:.4f} val_acc={mv['acuracia']:.4f} "
              f"val_f1={mv['f1_macro']:.4f} ({time.time()-t0:.0f}s)", flush=True)
        if mv["f1_macro"] > melhor:
            melhor, sem_melhora = mv["f1_macro"], 0
            torch.save({"estado": modelo.state_dict(), "classes": classes, "datasets": datasets,
                        "epoca": ep, "val_f1_macro": melhor}, caminho_ck)
        else:
            sem_melhora += 1
            if sem_melhora >= paciencia:
                print(f"  early stopping na época {ep} (melhor val F1-macro={melhor:.4f})")
                break

    info = {"nome": nome, "datasets": datasets or "todos", "classes": classes, "n_treino": len(treino),
            "n_val": len(val), "contagem_treino": {c: cont[c] for c in classes},
            "pesos_loss": dict(zip(classes, map(float, pesos))),
            "hiperparametros": {"arquitetura": "mobilenet_v3_large (ImageNet IMAGENET1K_V2)", "entrada": 224,
                                "epocas_max": epocas, "paciencia": paciencia, "batch": bs, "otimizador": "AdamW",
                                "lr_cabeca": lr, "lr_backbone": lr / 10, "weight_decay": wd,
                                "agendador": "OneCycle (cosine, 10% warmup)", "label_smoothing": 0.1,
                                "precisao": "AMP fp16" if disp.type == "cuda" else "fp32", "seed": C.SEED},
            "melhor_val_f1_macro": melhor, "historico": historico,
            "tempo_total_s": round(time.time() - t0, 1), "gpu": torch.cuda.get_device_name(0) if disp.type == "cuda" else None}
    C.RESULT.mkdir(parents=True, exist_ok=True)
    (C.RESULT / f"treino_{nome}.json").write_text(json.dumps(info, indent=2, ensure_ascii=False))
    return info


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--nome", default="combinado")
    ap.add_argument("--datasets", nargs="*", default=None, help="padrão: todos do manifest")
    ap.add_argument("--epocas", type=int, default=25)
    ap.add_argument("--paciencia", type=int, default=6)
    ap.add_argument("--bs", type=int, default=64)
    ap.add_argument("--lr", type=float, default=1e-3)
    a = ap.parse_args()
    treinar(a.nome, a.datasets, a.epocas, a.paciencia, a.bs, a.lr)


if __name__ == "__main__":
    main()
