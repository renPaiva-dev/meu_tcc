"""Exporta o checkpoint para ONNX e gera modelo/labels.json.

- opset 17, entrada "input" float32 [1,3,224,224] (NCHW), saída "logits" [1,7]
- verifica que o onnxruntime reproduz as saídas do PyTorch (tolerância 1e-4)
- gera versões quantizadas INT8: dinâmica (reciclathe-int8.onnx) e estática
  calibrada (reciclathe-int8-estatico.onnx); avaliar.py compara as três

Uso:
    .venv/bin/python src/exportar_onnx.py [--checkpoint modelo/checkpoints/combinado.pt]
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import comum as C  # noqa: E402
from mapeamento import CLASSES  # noqa: E402

VERSAO = "mobilenetv3-v1"


def verificar(onnx_path: Path, modelo: torch.nn.Module, tol: float) -> float:
    import onnxruntime as ort
    sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    g = torch.Generator().manual_seed(C.SEED)
    max_dif = 0.0
    for _ in range(8):
        x = torch.randn(1, 3, 224, 224, generator=g)
        with torch.no_grad():
            ref = modelo(x).numpy()
        out = sess.run(["logits"], {"input": x.numpy()})[0]
        assert out.shape == (1, len(CLASSES)), out.shape
        max_dif = max(max_dif, float(np.abs(out - ref).max()))
    print(f"  {onnx_path.name}: diferença máxima PyTorch x ONNX Runtime = {max_dif:.2e}")
    if max_dif > tol:
        raise SystemExit(f"ERRO: diferença {max_dif} acima da tolerância {tol}")
    return max_dif


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint", default=str(C.CKPT_DIR / "combinado.pt"))
    a = ap.parse_args()

    modelo, ck = C.carregar_checkpoint(Path(a.checkpoint))
    if ck["classes"] != CLASSES:
        raise SystemExit(f"checkpoint tem classes {ck['classes']}, esperado {CLASSES}")
    C.MODELO_DIR.mkdir(parents=True, exist_ok=True)
    fp32 = C.MODELO_DIR / "reciclathe.onnx"
    exemplo = torch.zeros(1, 3, 224, 224)
    torch.onnx.export(modelo, (exemplo,), str(fp32), input_names=["input"], output_names=["logits"],
                      opset_version=17, do_constant_folding=True, dynamo=False)

    import onnx
    m = onnx.load(str(fp32))
    onnx.checker.check_model(m)
    m.metadata_props.add(key="versao", value=VERSAO)
    m.metadata_props.add(key="classes", value=",".join(CLASSES))
    onnx.save(m, str(fp32))
    dif = verificar(fp32, modelo, tol=1e-4)

    # Quantização dinâmica INT8 (pesos em int8; ativações quantizadas em tempo de execução).
    from onnxruntime.quantization import QuantType, quant_pre_process, quantize_dynamic
    pre = C.MODELO_DIR / "_pre.onnx"
    quant_pre_process(str(fp32), str(pre), skip_symbolic_shape=True)
    int8 = C.MODELO_DIR / "reciclathe-int8.onnx"
    quantize_dynamic(str(pre), str(int8), weight_type=QuantType.QInt8)

    # Quantização ESTÁTICA INT8 (QDQ, por canal), calibrada com imagens de TREINO.
    # Em CPUs x86 a dinâmica costuma ser mais lenta para convoluções; a estática
    # quantiza também as ativações e em geral é mais rápida.
    from onnxruntime.quantization import CalibrationDataReader, QuantFormat, quantize_static

    class Calib(CalibrationDataReader):
        def __init__(self, n=300):
            import random
            from avaliar import preprocessar
            linhas = C.ler_manifest(split="treino")
            random.Random(C.SEED).shuffle(linhas)
            cfg = {"tamanho": 224, "media": C.MEDIA, "desvio": C.DESVIO}
            self.it = iter([{"input": preprocessar(C.RAIZ / l["caminho"], cfg)} for l in linhas[:n]])

        def get_next(self):
            return next(self.it, None)

    int8s = C.MODELO_DIR / "reciclathe-int8-estatico.onnx"
    quantize_static(str(pre), str(int8s), Calib(), quant_format=QuantFormat.QDQ, per_channel=True,
                    activation_type=QuantType.QUInt8, weight_type=QuantType.QInt8)
    pre.unlink()

    labels = {"versao": VERSAO, "classes": CLASSES,
              "entrada": {"tamanho": 224, "media": C.MEDIA, "desvio": C.DESVIO, "layout": "NCHW",
                          "escala": "pixel/255", "redimensionamento": "direto para 224x224 (sem recorte), RGB",
                          "nome_tensor": "input", "dtype": "float32"},
              "saida": {"nome_tensor": "logits", "forma": [1, len(CLASSES)], "observacao": "aplicar softmax"}}
    (C.MODELO_DIR / "labels.json").write_text(json.dumps(labels, indent=2, ensure_ascii=False) + "\n")
    info = {"checkpoint": str(Path(a.checkpoint).resolve().relative_to(C.RAIZ)), "epoca": ck["epoca"],
            "val_f1_macro": ck["val_f1_macro"], "opset": 17, "dif_max_pytorch_onnx": dif,
            "tamanho_fp32_mb": fp32.stat().st_size / 1e6, "tamanho_int8_mb": int8.stat().st_size / 1e6,
            "tamanho_int8_estatico_mb": int8s.stat().st_size / 1e6}
    (C.RESULT / "exportacao.json").write_text(json.dumps(info, indent=2))
    print(json.dumps(info, indent=2))


if __name__ == "__main__":
    main()
