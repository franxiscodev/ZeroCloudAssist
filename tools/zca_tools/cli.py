"""`uv run zca-indice …`: construir el índice del manual y evaluarlo. Rutas relativas a la raíz."""

from __future__ import annotations

import argparse
import hashlib
import statistics
import time
from datetime import date
from pathlib import Path

from zca_tools.construir import construir
from zca_tools.extraer import leer_manual
from zca_tools.tokens import contador_qwen
from zca_tools.trocear import trocear
from zca_tools.vectorizar import vectorizar

RAIZ = Path(__file__).resolve().parents[2]


def _ruta(texto: str) -> Path:
    ruta = Path(texto)
    return ruta if ruta.is_absolute() else RAIZ / ruta


def _sha256(ruta: Path) -> str:
    h = hashlib.sha256()
    with ruta.open("rb") as f:
        for bloque in iter(lambda: f.read(1 << 20), b""):
            h.update(bloque)
    return h.hexdigest()


def _construir(args: argparse.Namespace) -> None:
    inicio = time.perf_counter()
    contar = contador_qwen(_ruta(args.tokenizador))
    chunks = trocear(leer_manual(_ruta(args.pdf)), contar, objetivo=args.objetivo)
    vectores = vectorizar([f"passage: {c.texto}" for c in chunks], args.e5)
    gguf = _ruta(args.e5_gguf)
    meta = {
        "esquema": "1",
        "manual": "ABB ACS355 User's manual",
        "documento": _ruta(args.pdf).name,
        "version": args.version,
        "embeddings": gguf.stem,
        "embeddings_sha256": _sha256(gguf),
        "dimension": str(vectores.shape[1]),
        "tokens_objetivo": str(args.objetivo),
    }
    construir(_ruta(args.salida), chunks, vectores, meta)

    tokens = [c.tokens for c in chunks]
    print(f"{len(chunks)} chunks de {len({c.pagina for c in chunks})} páginas en "
          f"{time.perf_counter() - inicio:.0f} s → {_ruta(args.salida)}")
    print(f"tokens: media {statistics.mean(tokens):.1f} · mediana {statistics.median(tokens):.0f}"
          f" · mínimo {min(tokens)} · máximo {max(tokens)} · total {sum(tokens)}")
    for clave, valor in meta.items():
        print(f"  meta {clave} = {valor}")


def main() -> None:
    parser = argparse.ArgumentParser(prog="zca-indice")
    sub = parser.add_subparsers(required=True)

    p = sub.add_parser("construir", help="PDF → chunks → vectores → .sqlite")
    p.add_argument("--pdf", default="manuales/EN_ACS355_UM_E_A5.pdf")
    p.add_argument("--tokenizador", default="models/qwen2.5-tokenizer.json")
    p.add_argument("--e5", default="http://127.0.0.1:8090", help="URL de llama-server con e5")
    p.add_argument("--e5-gguf", default="models/multilingual-e5-small-q8_0.gguf",
                   help="el GGUF que sirve llama-server, para meta.embeddings_sha256")
    p.add_argument("--salida", default="models/acs355.sqlite")
    p.add_argument("--version", default=f"{date.today().isoformat()}.1")
    p.add_argument("--objetivo", type=int, default=130)
    p.set_defaults(accion=_construir)

    args = parser.parse_args()
    args.accion(args)


if __name__ == "__main__":
    main()
