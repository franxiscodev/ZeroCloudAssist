"""Compara los vectores de e5 en GGUF (llama-server) con la referencia de transformers.

Guarda de `tools/convertir_e5.py`: si el vocabulario o las posiciones se convirtieran mal, los
vectores del GGUF se alejarían de los del modelo original. Corre en el entorno del conversor:

    uv run --no-project --python 3.11 \
      --with-requirements third_party/llama.cpp/requirements/requirements-convert_hf_to_gguf.txt \
      python tools/verificar_e5.py models/hf/multilingual-e5-small http://127.0.0.1:8090

con `llama-server -m models/multilingual-e5-small-q8_0.gguf --embedding --pooling mean --port 8090`.
Sale con código 1 si algún coseno queda por debajo de 0,99.
"""

import json
import sys
import urllib.request

import torch
import torch.nn.functional as F
from transformers import AutoModel, AutoTokenizer

TEXTOS = [
    "query: el motor se calienta demasiado",
    "passage: Motor overtemperature",
    "passage: Fieldbus communication settings",
    "query: ¿Qué significa el fallo F0009 del variador?",
    "passage: 0009 MOT OVERTEMP Motor temperature estimation is too high.",
    "query: el armario está muy caliente y el variador se para",
    "passage: After disconnecting the input power, always wait for 5 minutes to let the "
    "intermediate circuit capacitors discharge before you start working on the drive.",
]
UMBRAL = 0.99


def referencia(dir_modelo: str) -> torch.Tensor:
    tok = AutoTokenizer.from_pretrained(dir_modelo)
    modelo = AutoModel.from_pretrained(dir_modelo).eval()
    lote = tok(TEXTOS, padding=True, truncation=True, max_length=512, return_tensors="pt")
    with torch.inference_mode():
        oculto = modelo(**lote).last_hidden_state
    mascara = lote["attention_mask"].unsqueeze(-1).float()
    return F.normalize((oculto * mascara).sum(1) / mascara.sum(1), dim=-1)


def servidor(url: str) -> torch.Tensor:
    cuerpo = json.dumps({"input": TEXTOS, "encoding_format": "float"}).encode("utf-8")
    peticion = urllib.request.Request(
        f"{url}/v1/embeddings", data=cuerpo, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(peticion) as r:
        datos = sorted(json.load(r)["data"], key=lambda d: d["index"])
    return F.normalize(torch.tensor([d["embedding"] for d in datos]), dim=-1)


def main() -> int:
    ref, gguf = referencia(sys.argv[1]), servidor(sys.argv[2])
    cosenos = (ref * gguf).sum(-1)
    for texto, c in zip(TEXTOS, cosenos.tolist()):
        print(f"{c:.5f}  {texto[:70]}")
    print(f"dimensión {gguf.shape[1]}, coseno mínimo {cosenos.min():.5f} (umbral {UMBRAL})")

    q, sobre, bus = gguf[0], gguf[1], gguf[2]
    print(f"cordura: sim(motor caliente, overtemperature) = {q @ sobre:.4f}, "
          f"sim(motor caliente, fieldbus) = {q @ bus:.4f}")
    ok = bool(cosenos.min() >= UMBRAL) and bool(q @ sobre > q @ bus) and gguf.shape[1] == 384
    print("OK" if ok else "FALLO")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
