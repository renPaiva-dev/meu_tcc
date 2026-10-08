"""Baixa os datasets públicos usados no classificador ReciclaTHE.

Cada dataset vai para uma pasta separada em ml/dados/brutos/<nome>/.
Os arquivos baixados são tratados como DADOS NÃO CONFIÁVEIS: este script
apenas baixa e extrai (com verificação de caminho contra path traversal);
nada do conteúdo baixado é executado.

Datasets (detalhes de licença em ml/DATASETS.md):
  - trashnet  : Hugging Face garythung/trashnet (MIT)
  - realwaste : UCI Machine Learning Repository, id 908 (CC BY 4.0)
  - taco      : Zenodo 10.5281/zenodo.3587843 (CC BY 4.0)
  - giz_ewaste: Hugging Face GIZ/e-waste-dataset-COCO-labels (CC BY 4.0)

Uso:
    .venv/bin/python -I src/baixar_dados.py [--so trashnet realwaste ...]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
BRUTOS = RAIZ / "dados" / "brutos"

FONTES = {
    "trashnet": {
        "url": "https://huggingface.co/datasets/garythung/trashnet/resolve/"
               "94cd17ebfd6702bf62281d3c89f22ff649815b46/dataset-resized.zip",
        "arquivo": "dataset-resized.zip",
    },
    "realwaste": {
        "url": "https://archive.ics.uci.edu/static/public/908/realwaste.zip",
        "arquivo": "realwaste.zip",
    },
    "taco": {
        "url": "https://zenodo.org/records/3587843/files/TACO.zip?download=1",
        "arquivo": "TACO.zip",
    },
}

GIZ_REPO = "GIZ/e-waste-dataset-COCO-labels"


def baixar(url: str, destino: Path) -> None:
    """Baixa url para destino (retoma se já existir completo)."""
    if destino.exists() and destino.stat().st_size > 0:
        print(f"  já existe: {destino.name} ({destino.stat().st_size/1e6:.1f} MB)")
        return
    tmp = destino.with_suffix(destino.suffix + ".parcial")
    print(f"  baixando {url}", flush=True)
    total = None
    for tentativa in range(1, 31):
        ja = tmp.stat().st_size if tmp.exists() else 0
        if total is not None and ja >= total:
            break
        cab = {"User-Agent": "ReciclaTHE-TCC/1.0"}
        if ja:
            cab["Range"] = f"bytes={ja}-"  # retoma download interrompido
        try:
            req = urllib.request.Request(url, headers=cab)
            with urllib.request.urlopen(req, timeout=120) as r:
                if ja and r.status != 206:  # servidor não aceitou Range: recomeça
                    ja = 0
                tamanho = r.headers.get("Content-Length")
                if tamanho is not None:
                    total = ja + int(tamanho)
                with open(tmp, "ab" if ja else "wb") as f:
                    lido = ja
                    while True:
                        bloco = r.read(1 << 20)
                        if not bloco:
                            break
                        f.write(bloco)
                        lido += len(bloco)
                        if lido % (100 << 20) < (1 << 20):
                            print(f"    {lido/1e6:.0f}/{(total or 0)/1e6:.0f} MB", flush=True)
            if total is None or tmp.stat().st_size >= total:
                break
            print(f"    conexão interrompida em {tmp.stat().st_size/1e6:.0f} MB, retomando", flush=True)
        except OSError as e:
            print(f"    erro ({e}); tentativa {tentativa}, retomando", flush=True)
    if total is not None and tmp.stat().st_size != total:
        raise RuntimeError(f"download incompleto: {tmp}")
    tmp.rename(destino)


def extrair_zip_seguro(zip_path: Path, pasta: Path) -> None:
    """Extrai o zip recusando membros fora da pasta (path traversal) e links."""
    marcador = pasta / ".extraido"
    if marcador.exists():
        print(f"  já extraído: {zip_path.name}")
        return
    base = pasta.resolve()
    with zipfile.ZipFile(zip_path) as z:
        for info in z.infolist():
            alvo = (pasta / info.filename).resolve()
            if not str(alvo).startswith(str(base) + "/") and alvo != base:
                raise RuntimeError(f"membro suspeito no zip: {info.filename}")
            # bits de modo de link simbólico (unix) -> recusa
            if (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise RuntimeError(f"link simbólico no zip: {info.filename}")
        z.extractall(pasta)
    # zips aninhados (RealWaste/TrashNet às vezes vêm assim)
    for interno in list(pasta.rglob("*.zip")):
        if interno == zip_path or "__MACOSX" in interno.parts:
            continue
        sub = interno.with_suffix("")
        sub.mkdir(exist_ok=True)
        extrair_zip_seguro(interno, sub)
    marcador.write_text("ok")


def baixar_giz() -> None:
    """Baixa anotações COCO + imagens do dataset GIZ (via huggingface_hub)."""
    from huggingface_hub import HfApi, hf_hub_download

    pasta = BRUTOS / "giz_ewaste"
    pasta.mkdir(parents=True, exist_ok=True)
    api = HfApi()
    info = api.dataset_info(GIZ_REPO)
    revisao = info.sha
    arquivos = sorted(s.rfilename for s in info.siblings)
    (pasta / "REVISAO.txt").write_text(f"{GIZ_REPO}@{revisao}\n")
    alvos = [a for a in arquivos if a.endswith(("result.json", ".jpg", ".JPG", ".jpeg", ".JPEG", ".png"))]
    print(f"  {len(alvos)} arquivos em {GIZ_REPO}@{revisao[:8]}")
    for i, a in enumerate(alvos):
        hf_hub_download(GIZ_REPO, a, repo_type="dataset", revision=revisao, local_dir=pasta)
        if i % 100 == 0:
            print(f"    {i}/{len(alvos)}", flush=True)


def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--so", nargs="*", default=["trashnet", "realwaste", "taco", "giz_ewaste"])
    args = ap.parse_args()
    BRUTOS.mkdir(parents=True, exist_ok=True)
    registro = {}
    for nome in args.so:
        print(f"[{nome}]")
        if nome == "giz_ewaste":
            baixar_giz()
            continue
        fonte = FONTES[nome]
        pasta = BRUTOS / nome
        pasta.mkdir(parents=True, exist_ok=True)
        zp = pasta / fonte["arquivo"]
        baixar(fonte["url"], zp)
        registro[nome] = {"url": fonte["url"], "sha256": sha256(zp)}
        extrair_zip_seguro(zp, pasta)
    reg = BRUTOS / "downloads.json"
    antigo = json.loads(reg.read_text()) if reg.exists() else {}
    antigo.update(registro)
    reg.write_text(json.dumps(antigo, indent=2))
    print("ok")


if __name__ == "__main__":
    main()
