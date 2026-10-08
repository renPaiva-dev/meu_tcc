"""Experimento de generalização entre domínios (domain shift).

Treina modelos com subconjuntos de datasets e avalia no teste de cada dataset:
  - so_trashnet  : treino só TrashNet (fotos de estúdio, fundo branco)
  - so_realwaste : treino só RealWaste (fotos em aterro)
  - sem_taco     : TrashNet + RealWaste + GIZ (TACO fica totalmente fora; avaliado em TODO o TACO)
  - combinado    : todos (modelo principal; treinado por treinar.py)

Para comparar de forma justa, cada par (modelo, dataset alvo) é avaliado apenas nas
classes em COMUM, e o argmax é restrito a essas classes ("logits restritos").
Também se reporta a avaliação sem restrição (todas as classes do modelo).

Uso:
    .venv/bin/python src/experimento_dominio.py [--epocas 25] [--so-avaliar]
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import DataLoader

sys.path.insert(0, str(Path(__file__).resolve().parent))
import comum as C  # noqa: E402
from treinar import treinar  # noqa: E402

EXPERIMENTOS = {
    "so_trashnet": ["trashnet"],
    "so_realwaste": ["realwaste"],
    "sem_taco": ["trashnet", "realwaste", "giz_ewaste"],
}
ALVOS = ["trashnet", "realwaste", "taco"]


def logits(modelo, linhas, classes_modelo, disp):
    # 'linhas' já está filtrada para classes que o modelo conhece
    ds = C.DatasetManifest(linhas, classes_modelo, C.transform_avaliacao())
    dl = DataLoader(ds, batch_size=128, num_workers=10)
    out, ys = [], []
    with torch.no_grad(), torch.autocast("cuda", enabled=disp.type == "cuda"):
        for x, y in dl:
            out.append(modelo(x.to(disp)).float().cpu().numpy())
            ys.append(y.numpy())
    return np.concatenate(out), np.concatenate(ys)


def avaliar(nome: str, alvo: str, split: str, disp) -> dict | None:
    modelo, ck = C.carregar_checkpoint(C.CKPT_DIR / f"{nome}.pt", disp)
    modelo.to(disp)
    cm = ck["classes"]
    linhas_todas = C.ler_manifest([alvo], split)
    classes_alvo = sorted({l["classe"] for l in linhas_todas})
    comuns = [c for c in cm if c in classes_alvo]
    linhas = [l for l in linhas_todas if l["classe"] in comuns]
    if not linhas:
        return None
    lg, y = logits(modelo, linhas, cm, disp)
    idx_comuns = [cm.index(c) for c in comuns]
    # restrito: argmax só nas classes comuns; rótulos reindexados para 'comuns'
    p_restr = np.array(idx_comuns)[lg[:, idx_comuns].argmax(1)]
    mapa = {cm.index(c): i for i, c in enumerate(comuns)}
    y_r = np.array([mapa[v] for v in y])
    p_r = np.array([mapa[v] for v in p_restr])
    restrito = C.metricas(y_r, p_r, comuns)
    # sem restrição: argmax em todas as classes do modelo
    livre = C.metricas(y, lg.argmax(1), cm)
    return {"modelo": nome, "alvo": alvo, "split": split, "classes_comuns": comuns, "n": len(linhas),
            "restrito": {k: restrito[k] for k in ("acuracia", "f1_macro", "por_classe")},
            "sem_restricao": {k: livre[k] for k in ("acuracia", "f1_macro")}}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--epocas", type=int, default=25)
    ap.add_argument("--so-avaliar", action="store_true", help="não treina; usa checkpoints existentes")
    a = ap.parse_args()
    disp = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if not a.so_avaliar:
        for nome, dss in EXPERIMENTOS.items():
            treinar(nome, dss, epocas=a.epocas)
    resultados = []
    for nome in [*EXPERIMENTOS, "combinado"]:
        if not (C.CKPT_DIR / f"{nome}.pt").exists():
            print(f"(sem checkpoint {nome}, pulando)")
            continue
        for alvo in ALVOS:
            # TACO nunca visto pelo 'sem_taco' -> usa o dataset TACO inteiro como teste
            split = None if (nome == "sem_taco" and alvo == "taco") else "teste"
            r = avaliar(nome, alvo, split, disp)
            if r:
                resultados.append(r)
                print(f"{nome:13s} -> {alvo:10s} ({split or 'tudo'}, n={r['n']}, {len(r['classes_comuns'])} cl.): "
                      f"acc={r['restrito']['acuracia']:.4f} f1={r['restrito']['f1_macro']:.4f} "
                      f"| sem restrição acc={r['sem_restricao']['acuracia']:.4f}")
        # comparação justa extra: TACO de TESTE para todos os modelos
        if nome == "sem_taco":
            r = avaliar(nome, "taco", "teste", disp)
            if r:
                r["obs"] = "apenas split de teste do TACO (mesmo conjunto do combinado)"
                resultados.append(r)
                print(f"{nome:13s} -> taco       (teste, n={r['n']}): acc={r['restrito']['acuracia']:.4f} "
                      f"f1={r['restrito']['f1_macro']:.4f}")
    (C.RESULT / "dominio.json").write_text(json.dumps(resultados, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
