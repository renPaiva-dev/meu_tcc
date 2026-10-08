"""Utilidades compartilhadas: seed, dataset do manifest, transforms, modelo e métricas."""
from __future__ import annotations

import csv
import os
import random
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from torch import nn
from torch.utils.data import Dataset
from torchvision import models, transforms as T

RAIZ = Path(__file__).resolve().parent.parent
MANIFEST = RAIZ / "dados" / "manifest.csv"
MODELO_DIR = RAIZ / "modelo"
CKPT_DIR = MODELO_DIR / "checkpoints"
RESULT = RAIZ / "resultados"

TAMANHO = 224
MEDIA = [0.485, 0.456, 0.406]
DESVIO = [0.229, 0.224, 0.225]
SEED = 42


def fixar_seed(seed: int = SEED) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    torch.backends.cudnn.benchmark = True  # mais rápido; pequena não-determinância em GPU


def ler_manifest(datasets: list[str] | None = None, split: str | None = None,
                 classes: list[str] | None = None) -> list[dict]:
    with open(MANIFEST) as f:
        linhas = list(csv.DictReader(f))
    return [l for l in linhas
            if (datasets is None or l["dataset_origem"] in datasets)
            and (split is None or l["split"] == split)
            and (classes is None or l["classe"] in classes)]


# Pré-processamento de inferência = o mesmo descrito em modelo/labels.json:
# redimensiona direto para 224x224 (sem recorte), pixel/255, normaliza ImageNet.
def transform_avaliacao():
    return T.Compose([T.Resize((TAMANHO, TAMANHO), antialias=True), T.ToTensor(), T.Normalize(MEDIA, DESVIO)])


def transform_treino():
    return T.Compose([
        T.RandomResizedCrop(TAMANHO, scale=(0.5, 1.0), ratio=(0.75, 1.333), antialias=True),
        T.RandomHorizontalFlip(),
        T.RandomVerticalFlip(p=0.2),
        T.RandomRotation(20),
        T.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.3, hue=0.03),
        T.RandomApply([T.GaussianBlur(5, sigma=(0.1, 1.5))], p=0.2),
        T.ToTensor(),
        T.Normalize(MEDIA, DESVIO),
        T.RandomErasing(p=0.2, scale=(0.02, 0.15)),
    ])


class DatasetManifest(Dataset):
    def __init__(self, linhas: list[dict], classes: list[str], transform):
        self.linhas = linhas
        self.idx = {c: i for i, c in enumerate(classes)}
        self.transform = transform

    def __len__(self):
        return len(self.linhas)

    def __getitem__(self, i):
        l = self.linhas[i]
        with Image.open(RAIZ / l["caminho"]) as im:
            im = im.convert("RGB")
        return self.transform(im), self.idx[l["classe"]]


def criar_modelo(n_classes: int, pretreinado: bool = True) -> nn.Module:
    pesos = models.MobileNet_V3_Large_Weights.IMAGENET1K_V2 if pretreinado else None
    m = models.mobilenet_v3_large(weights=pesos)
    m.classifier[3] = nn.Linear(m.classifier[3].in_features, n_classes)
    return m


def carregar_checkpoint(caminho: Path, dispositivo="cpu"):
    ck = torch.load(caminho, map_location=dispositivo, weights_only=False)
    m = criar_modelo(len(ck["classes"]), pretreinado=False)
    m.load_state_dict(ck["estado"])
    m.eval()
    return m, ck


def metricas(y_true, y_pred, classes: list[str]) -> dict:
    from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_recall_fscore_support
    rotulos = list(range(len(classes)))
    p, r, f, s = precision_recall_fscore_support(y_true, y_pred, labels=rotulos, zero_division=0)
    presentes = [i for i in rotulos if s[i] > 0]
    return {
        "n": int(len(y_true)),
        "acuracia": float(accuracy_score(y_true, y_pred)),
        # F1-macro só sobre as classes presentes no conjunto avaliado
        "f1_macro": float(f1_score(y_true, y_pred, labels=presentes, average="macro", zero_division=0)),
        "por_classe": {classes[i]: {"precisao": float(p[i]), "revocacao": float(r[i]), "f1": float(f[i]),
                                    "suporte": int(s[i])} for i in rotulos},
        "matriz_confusao": confusion_matrix(y_true, y_pred, labels=rotulos).tolist(),
        "classes": classes,
    }
