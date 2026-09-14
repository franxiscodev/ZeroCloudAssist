"""Vectores de multilingual-e5-small con llama-server (`--embedding --pooling mean`).

Endpoint `/v1/embeddings` compatible con OpenAI, según `tools/server/README.md` del tag b10941:
`{"input": [...]}` → `{"data": [{"index": i, "embedding": [...]}]}`. El servidor ya normaliza,
pero se normaliza siempre aquí para no depender de su configuración (`--embd-normalize`).
El llamador antepone "query: " o "passage: ", como exige e5.
"""

from __future__ import annotations

import numpy as np
import requests


def normalizar(v: np.ndarray) -> np.ndarray:
    v = np.asarray(v, dtype=np.float32)
    norma = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.where(norma == 0, 1, norma)


def vectorizar(textos: list[str], url: str, lote: int = 32) -> np.ndarray:
    """(n, 384) float32 con norma L2 = 1."""
    filas: list[list[float]] = []
    for i in range(0, len(textos), lote):
        respuesta = requests.post(
            f"{url}/v1/embeddings",
            json={"input": textos[i : i + lote], "encoding_format": "float"},
            timeout=120,
        )
        respuesta.raise_for_status()
        datos = sorted(respuesta.json()["data"], key=lambda d: d["index"])
        filas += [d["embedding"] for d in datos]
    return normalizar(np.array(filas, dtype=np.float32))
