"""Prepara os dados: mapeia classes, recorta objetos (TACO/GIZ), deduplica,
redimensiona e faz o split estratificado 70/15/15 POR DATASET.

Saídas:
  ml/dados/processados/<dataset>/<classe>/<id>.jpg   (lado menor = 256 px)
  ml/dados/manifest.csv   (caminho, classe, dataset_origem, split, grupo, classe_origem)
  ml/resultados/distribuicao.json / distribuicao.md

Uso:
    .venv/bin/python -I src/preparar_dados.py
"""
from __future__ import annotations

import csv
import hashlib
import json
import os
import sys
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps
from sklearn.model_selection import StratifiedGroupKFold

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mapeamento as M  # noqa: E402

SEED = 42
LADO_MENOR = 256
MIN_LADO_RECORTE = 48     # recortes menores que isso (px na imagem original) são descartados
MARGEM_RECORTE = 0.10     # margem de contexto ao redor da bounding box
Image.MAX_IMAGE_PIXELS = 120_000_000  # proteção contra "decompression bomb"

RAIZ = Path(__file__).resolve().parent.parent
BRUTOS = RAIZ / "dados" / "brutos"
PROC = RAIZ / "dados" / "processados"
MANIFEST = RAIZ / "dados" / "manifest.csv"
RESULT = RAIZ / "resultados"
EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


# ---------------------------------------------------------------- descoberta
def achar_pasta_com_classes(base: Path, nomes: set[str]) -> Path:
    """Encontra o diretório que contém subpastas com os nomes de classe."""
    for d in sorted([base, *[p for p in base.rglob("*") if p.is_dir()]]):
        if "__MACOSX" in d.parts:
            continue
        filhos = {c.name for c in d.iterdir() if c.is_dir()}
        if len(filhos & nomes) >= len(nomes) - 1:
            return d
    raise FileNotFoundError(f"não achei pastas {sorted(nomes)} em {base}")


def itens_pastas(nome_ds: str, mapa: dict) -> list[dict]:
    base = achar_pasta_com_classes(BRUTOS / nome_ds, set(mapa))
    itens = []
    for cls_origem, cls in mapa.items():
        pasta = base / cls_origem
        if cls is None or not pasta.exists():
            continue
        for f in sorted(pasta.iterdir()):
            if f.suffix.lower() in EXT and not f.name.startswith("."):
                itens.append(dict(origem=str(f), bbox=None, classe=cls, classe_origem=cls_origem,
                                  dataset=nome_ds, grupo=f"{nome_ds}/{f.name}"))
    return itens


def itens_coco(nome_ds: str, ann_path: Path, pasta_imgs_fn, mapa: dict) -> list[dict]:
    d = json.loads(ann_path.read_text())
    cats = {c["id"]: c["name"] for c in d["categories"]}
    imgs = {i["id"]: i for i in d["images"]}
    itens = []
    nao_mapeadas = Counter()
    for a in d["annotations"]:
        nome = cats[a["category_id"]]
        if nome not in mapa:
            nao_mapeadas[nome] += 1
            continue
        cls = mapa[nome]
        if cls is None:
            continue
        im = imgs[a["image_id"]]
        caminho = pasta_imgs_fn(im["file_name"])
        if caminho is None or not caminho.exists():
            continue
        x, y, w, h = a["bbox"]
        if min(w, h) < MIN_LADO_RECORTE:
            continue
        itens.append(dict(origem=str(caminho), bbox=[x, y, w, h], ann_wh=[im["width"], im["height"]],
                          classe=cls, classe_origem=nome, dataset=nome_ds,
                          grupo=f"{nome_ds}/{im['file_name']}", ann_id=a["id"]))
    if nao_mapeadas:
        print(f"  [{nome_ds}] categorias sem mapeamento (ignoradas): {dict(nao_mapeadas)}")
    return itens


def itens_taco() -> list[dict]:
    base = BRUTOS / "taco"
    ann = sorted(base.rglob("annotations.json"))
    if not ann:
        print("  [taco] não encontrado, pulando")
        return []
    ann = ann[0]
    raiz_imgs = ann.parent
    return itens_coco("taco", ann, lambda fn: raiz_imgs / fn, M.TACO)


def itens_giz() -> list[dict]:
    base = BRUTOS / "giz_ewaste"
    ann = sorted(base.rglob("result.json"))
    if not ann:
        print("  [giz_ewaste] não encontrado, pulando")
        return []
    pasta = ann[0].parent / "images"
    return itens_coco("giz_ewaste", ann[0], lambda fn: pasta / os.path.basename(fn), M.GIZ)


# ---------------------------------------------------------------- processamento
def dhash(img: Image.Image) -> str:
    g = np.asarray(img.convert("L").resize((9, 8), Image.BILINEAR), dtype=np.int16)
    bits = (g[:, 1:] > g[:, :-1]).flatten()
    return "%016x" % int("".join("1" if b else "0" for b in bits), 2)


def processar(item: dict) -> dict | None:
    try:
        with Image.open(item["origem"]) as im0:
            im0.load()
            im = ImageOps.exif_transpose(im0)
            if item["bbox"] is not None:
                aw, ah = item["ann_wh"]
                if im.size != (aw, ah) and im0.size == (aw, ah):
                    im = im0  # anotação feita sobre a imagem sem rotação EXIF
                if im.size != (aw, ah):
                    return None
                x, y, w, h = item["bbox"]
                mx, my = w * MARGEM_RECORTE, h * MARGEM_RECORTE
                caixa = (max(0, int(x - mx)), max(0, int(y - my)),
                         min(aw, int(x + w + mx)), min(ah, int(y + h + my)))
                im = im.crop(caixa)
            im = im.convert("RGB")
    except Exception as e:  # arquivo corrompido / formato inesperado
        return {"erro": f"{item['origem']}: {e}"}
    w, h = im.size
    esc = LADO_MENOR / min(w, h)
    if esc < 1:
        im = im.resize((max(1, round(w * esc)), max(1, round(h * esc))), Image.LANCZOS)
    dh = dhash(im)
    chave = item["origem"] + str(item.get("ann_id", ""))
    nome = hashlib.sha1(chave.encode()).hexdigest()[:16] + ".jpg"
    destino = PROC / item["dataset"] / item["classe"] / nome
    destino.parent.mkdir(parents=True, exist_ok=True)
    im.save(destino, "JPEG", quality=92)
    md5 = hashlib.md5(destino.read_bytes()).hexdigest()
    return {**item, "caminho": str(destino.relative_to(RAIZ)), "dhash": dh, "md5": md5}


# ---------------------------------------------------------------- split
def split_por_dataset(linhas: list[dict]) -> None:
    """70/15/15 estratificado por classe, agrupado por imagem de origem
    (recortes da mesma foto ficam no mesmo split), separado por dataset."""
    por_ds = defaultdict(list)
    for l in linhas:
        por_ds[l["dataset"]].append(l)
    for ds, ls in por_ds.items():
        y = np.array([l["classe"] for l in ls])
        g = np.array([l["grupo"] for l in ls])
        # 20 dobras -> 3 dobras teste (15%), 3 validação (15%), 14 treino (70%)
        n_spl = 20
        menor = min(Counter(y).values())
        if menor < n_spl:
            print(f"  [{ds}] aviso: classe com {menor} amostras (<{n_spl}); estratificação aproximada")
        sgkf = StratifiedGroupKFold(n_splits=n_spl, shuffle=True, random_state=SEED)
        for k, (_, idx) in enumerate(sgkf.split(np.zeros(len(ls)), y, g)):
            s = "teste" if k < 3 else ("val" if k < 6 else "treino")
            for i in idx:
                ls[i]["split"] = s


def main() -> None:
    PROC.mkdir(parents=True, exist_ok=True)
    RESULT.mkdir(parents=True, exist_ok=True)
    itens = []
    if (BRUTOS / "trashnet").exists():
        itens += itens_pastas("trashnet", M.TRASHNET)
    if (BRUTOS / "realwaste").exists():
        itens += itens_pastas("realwaste", M.REALWASTE)
    itens += itens_taco()
    itens += itens_giz()
    print(f"itens candidatos: {len(itens)}  {Counter(i['dataset'] for i in itens)}")

    with ProcessPoolExecutor(max_workers=min(12, os.cpu_count() or 4)) as ex:
        res = list(ex.map(processar, itens, chunksize=32))
    erros = [r["erro"] for r in res if r and "erro" in r]
    linhas = [r for r in res if r and "erro" not in r]
    descart_orient = sum(1 for r in res if r is None)
    print(f"processados: {len(linhas)}  erros: {len(erros)}  descartados (orientação/bbox): {descart_orient}")

    # ---- deduplicação: md5 exato e dHash idêntico (quase-duplicatas),
    # inclusive ENTRE datasets. Mantém a primeira ocorrência (ordem determinística).
    vistos_md5, vistos_dh, unicos = set(), {}, []
    dup = Counter()
    conflito = 0
    for l in linhas:
        if l["md5"] in vistos_md5:
            dup[l["dataset"]] += 1
            continue
        if l["dhash"] in vistos_dh:
            dup[l["dataset"]] += 1
            if vistos_dh[l["dhash"]] != l["classe"]:
                conflito += 1
            continue
        vistos_md5.add(l["md5"])
        vistos_dh[l["dhash"]] = l["classe"]
        unicos.append(l)
    print(f"duplicatas removidas: {dict(dup)} (com rótulos conflitantes: {conflito})")

    split_por_dataset(unicos)

    campos = ["caminho", "classe", "dataset_origem", "split", "grupo", "classe_origem"]
    with open(MANIFEST, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=campos)
        w.writeheader()
        for l in sorted(unicos, key=lambda r: r["caminho"]):
            w.writerow({"caminho": l["caminho"], "classe": l["classe"], "dataset_origem": l["dataset"],
                        "split": l["split"], "grupo": l["grupo"], "classe_origem": l["classe_origem"]})

    # ---- distribuição
    dist = defaultdict(lambda: defaultdict(Counter))
    for l in unicos:
        dist[l["dataset"]][l["split"]][l["classe"]] += 1
        dist["TOTAL"][l["split"]][l["classe"]] += 1
    resumo = {
        "candidatos": len(itens), "processados": len(linhas), "erros": len(erros),
        "descartados_orientacao": descart_orient, "duplicatas": dict(dup),
        "duplicatas_rotulo_conflitante": conflito, "finais": len(unicos),
        "distribuicao": {ds: {s: dict(c) for s, c in v.items()} for ds, v in dist.items()},
        "origem_por_classe": {ds: dict(Counter(f"{l['classe_origem']}->{l['classe']}" for l in unicos
                                               if l["dataset"] == ds)) for ds in dist if ds != "TOTAL"},
    }
    (RESULT / "distribuicao.json").write_text(json.dumps(resumo, indent=2, ensure_ascii=False))

    md = ["| Dataset | Split | " + " | ".join(M.CLASSES) + " | Total |",
          "|---|---|" + "---|" * (len(M.CLASSES) + 1)]
    for ds in [*sorted(k for k in dist if k != "TOTAL"), "TOTAL"]:
        for s in ["treino", "val", "teste"]:
            c = dist[ds][s]
            md.append(f"| {ds} | {s} | " + " | ".join(str(c.get(k, 0)) for k in M.CLASSES)
                      + f" | {sum(c.values())} |")
    (RESULT / "distribuicao.md").write_text("\n".join(md) + "\n")
    print("\n".join(md))
    if erros:
        (RESULT / "erros_preparo.txt").write_text("\n".join(erros))


if __name__ == "__main__":
    main()
