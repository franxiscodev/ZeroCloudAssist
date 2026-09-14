"""`uv run zca-indice …`: construir el índice del manual y evaluarlo. Rutas relativas a la raíz."""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import statistics
import time
from contextlib import closing
from datetime import date
from pathlib import Path

import numpy as np
import yaml

from zca_tools.construir import construir
from zca_tools.evaluar import acierta, priorizar, rrf, seleccionar
from zca_tools.extraer import leer_manual
from zca_tools.terminos import (
    clase_literal,
    consulta_fts,
    leer_glosario,
    palabras_clave,
    terminos_literales,
)
from zca_tools.tokens import contador_qwen
from zca_tools.traducir import traducir
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
    glosario = leer_glosario(_ruta(args.glosario))
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
        "capitulo_codigos": glosario.capitulos.get("codigo", ""),
        "capitulo_parametros": glosario.capitulos.get("parametro", ""),
    }
    construir(_ruta(args.salida), chunks, vectores, meta, glosario.terminos)
    print(f"glosario: {len(glosario.terminos)} entradas")

    tokens = [c.tokens for c in chunks]
    print(f"{len(chunks)} chunks de {len({c.pagina for c in chunks})} páginas en "
          f"{time.perf_counter() - inicio:.0f} s → {_ruta(args.salida)}")
    print(f"tokens: media {statistics.mean(tokens):.1f} · mediana {statistics.median(tokens):.0f}"
          f" · mínimo {min(tokens)} · máximo {max(tokens)} · total {sum(tokens)}")
    for clave, valor in meta.items():
        print(f"  meta {clave} = {valor}")


def _bateria(ruta: str) -> list[dict]:
    return yaml.safe_load(_ruta(ruta).read_text(encoding="utf-8"))


def _vectores_bateria(args: argparse.Namespace) -> None:
    """Vectores de "query: " + pregunta, para comparar con los del móvil (plan 02, paso 2.3)."""
    preguntas = _bateria(args.bateria)
    vectores = vectorizar([f"query: {p['pregunta']}" for p in preguntas], args.e5)
    datos = [{"id": p["id"], "pregunta": p["pregunta"], "vector": v.tolist()}
             for p, v in zip(preguntas, vectores)]
    _ruta(args.salida).write_text(json.dumps(datos, ensure_ascii=False), encoding="utf-8")
    print(f"{len(datos)} vectores → {_ruta(args.salida)}")


def _traducir(args: argparse.Namespace) -> None:
    """Traduce las preguntas con Qwen (llama-server con el GGUF de Qwen) y las guarda."""
    traducciones = {}
    for p in _bateria(args.bateria):
        inicio = time.perf_counter()
        t = traducir(p["pregunta"], args.url)
        traducciones[p["id"]] = t
        print(f"{p['id']} {time.perf_counter() - inicio:4.1f} s · {t['tokens_prompt']} + "
              f"{t['tokens_respuesta']} tok · {p['pregunta']} → {t['en']}")
    _ruta(args.salida).write_text(json.dumps(traducciones, ensure_ascii=False, indent=1),
                                  encoding="utf-8")
    print(f"→ {_ruta(args.salida)}")


def _evaluar(args: argparse.Namespace) -> None:
    """Páginas que entrarían en el prompt con FTS5, con vectores y con la híbrida (RRF)."""
    preguntas = _bateria(args.bateria)
    traducciones = {}
    if args.traducciones:
        datos = json.loads(_ruta(args.traducciones).read_text(encoding="utf-8"))
        traducciones = {id_: t["en"] for id_, t in datos.items()}
    if (args.fts == "traduccion" or args.vectores == "traduccion") and not traducciones:
        raise SystemExit("--fts/--vectores traduccion necesitan --traducciones")
    with closing(sqlite3.connect(f"file:{_ruta(args.indice).as_posix()}?mode=ro", uri=True)) as con:
        filas = con.execute(
            "SELECT id, pagina, tokens, vector, texto, capitulo FROM chunks ORDER BY id"
        ).fetchall()
        ids = [f[0] for f in filas]
        pagina = {f[0]: f[1] for f in filas}
        tokens = {f[0]: f[2] for f in filas}
        textos = {f[0]: f[4] for f in filas}
        capitulos = {f[0]: f[5] for f in filas}
        matriz = np.stack([np.frombuffer(f[3], dtype="<f4") for f in filas])
        glosario = [(tuple(es.split("|")), tuple(en.split("|")))
                    for es, en in con.execute("SELECT es, en FROM glosario ORDER BY rowid")]
        meta = dict(con.execute("SELECT clave, valor FROM meta"))
        preferido = {"codigo": meta.get("capitulo_codigos"), "parametro": meta.get("capitulo_parametros")}
        textos_vec = [traducciones[p["id"]] if args.vectores == "traduccion" else p["pregunta"]
                      for p in preguntas]
        consultas = vectorizar([f"query: {t}" for t in textos_vec], args.e5)

        aciertos = {"fts": 0, "vectores": 0, "hibrida": 0}
        print("| ID | Tipo | Esperadas | FTS5 | Vectores | Híbrida |")
        print("| --- | --- | --- | --- | --- | --- |")
        for p, q in zip(preguntas, consultas):
            if args.fts == "glosario":
                fts_query = consulta_fts(p["pregunta"], glosario)
            elif args.fts == "traduccion":
                fts_query = consulta_fts(p["pregunta"], claves=palabras_clave(traducciones[p["id"]]))
            else:
                fts_query = consulta_fts(p["pregunta"])
            # Todos los resultados de FTS5 por bm25, reordenados (entrada que define el código
            # primero) y después recortados a k.
            todos = [r[0] for r in con.execute(
                "SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY rank", (fts_query,)
            )] if fts_query else []
            fts = priorizar(todos, textos, capitulos, terminos_literales(p["pregunta"]),
                            preferido.get(clase_literal(p["pregunta"])))[: args.k]
            vec = [ids[i] for i in np.argsort(-(matriz @ q))[: args.k]]
            celdas = []
            for metodo, ranking in (("fts", fts), ("vectores", vec), ("hibrida", rrf([fts, vec]))):
                paginas = [pagina[i] for i in seleccionar(ranking, tokens)]
                ok = acierta(paginas, p["paginas_esperadas"])
                if p["tipo"] != "fuera":
                    aciertos[metodo] += ok
                marca = "" if p["tipo"] == "fuera" else (" ✓" if ok else " ✗")
                celdas.append(", ".join(map(str, paginas)) + marca if paginas else "—" + marca)
            print(f"| {p['id']} | {p['tipo']} | {', '.join(map(str, p['paginas_esperadas'])) or '—'} | "
                  + " | ".join(celdas) + " |")

    total = sum(p["tipo"] != "fuera" for p in preguntas)
    print(f"\nRecall@2 (sin la de fuera, {total} preguntas): "
          + " · ".join(f"{m} {n}/{total}" for m, n in aciertos.items()))


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
    p.add_argument("--glosario", default="docs/glosario-taller.yaml")
    p.add_argument("--version", default=f"{date.today().isoformat()}.1")
    p.add_argument("--objetivo", type=int, default=130)
    p.set_defaults(accion=_construir)

    p = sub.add_parser("evaluar", help="recall@2 de FTS5, vectores e híbrida con la batería")
    p.add_argument("--bateria", default="docs/bateria-manual.yaml")
    p.add_argument("--indice", default="models/acs355.sqlite")
    p.add_argument("--e5", default="http://127.0.0.1:8090")
    p.add_argument("--k", type=int, default=10, help="candidatos de cada método antes de fusionar")
    p.add_argument("--traducciones", help="JSON de `zca-indice traducir`")
    p.add_argument("--fts", choices=["glosario", "literales", "traduccion"], default="glosario",
                   help="literales de la pregunta + glosario, solo literales, o + palabras de la traducción")
    p.add_argument("--vectores", choices=["original", "traduccion"], default="original",
                   help="vectorizar la pregunta en español o su traducción")
    p.set_defaults(accion=_evaluar)

    p = sub.add_parser("traducir", help="traduce las preguntas con Qwen (llama-server de Qwen)")
    p.add_argument("--bateria", default="docs/bateria-manual.yaml")
    p.add_argument("--url", default="http://127.0.0.1:8090")
    p.add_argument("--salida", required=True)
    p.set_defaults(accion=_traducir)

    p = sub.add_parser("vectores-bateria", help="vectores de las preguntas para el paso 2.3")
    p.add_argument("--bateria", default="docs/bateria-manual.yaml")
    p.add_argument("--e5", default="http://127.0.0.1:8090")
    p.add_argument("--salida", default="models/bateria-vectores.json")
    p.set_defaults(accion=_vectores_bateria)

    args = parser.parse_args()
    args.accion(args)


if __name__ == "__main__":
    main()
