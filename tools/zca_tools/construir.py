"""Escribe el `.sqlite` del manual (esquema v1 del plan 02): meta, chunks con vector y FTS5."""

from __future__ import annotations

import sqlite3
from contextlib import closing
from pathlib import Path

import numpy as np

from zca_tools.trocear import Chunk

CLAVES_META = (
    "esquema", "manual", "documento", "version",
    "embeddings", "embeddings_sha256", "dimension", "tokens_objetivo",
)

_ESQUEMA = """
CREATE TABLE meta (clave TEXT PRIMARY KEY, valor TEXT NOT NULL);
CREATE TABLE chunks (
  id       INTEGER PRIMARY KEY,
  pagina   INTEGER NOT NULL,
  capitulo TEXT    NOT NULL,
  texto    TEXT    NOT NULL,
  tokens   INTEGER NOT NULL,
  vector   BLOB    NOT NULL          -- float32 little-endian, norma L2 = 1, de "passage: " + texto
);
CREATE VIRTUAL TABLE chunks_fts USING fts5(
  texto, content='chunks', content_rowid='id', tokenize='unicode61'
);
"""


def construir(ruta: Path, chunks: list[Chunk], vectores: np.ndarray, meta: dict[str, str]) -> None:
    faltan = [clave for clave in CLAVES_META if clave not in meta]
    if faltan:
        raise ValueError(f"faltan claves en meta: {', '.join(faltan)}")
    v = np.asarray(vectores, dtype="<f4")
    if v.shape != (len(chunks), int(meta["dimension"])):
        raise ValueError(f"vectores {v.shape} para {len(chunks)} chunks de {meta['dimension']}")

    # Se escribe aparte y se sustituye al final: un fallo a medias no deja un índice roto.
    temporal = ruta.with_name(ruta.name + ".tmp")
    temporal.unlink(missing_ok=True)
    with closing(sqlite3.connect(temporal)) as con:
        con.executescript(_ESQUEMA)
        con.executemany("INSERT INTO meta VALUES (?, ?)", meta.items())
        con.executemany(
            "INSERT INTO chunks VALUES (?, ?, ?, ?, ?, ?)",
            (
                (i, c.pagina, c.capitulo, c.texto, c.tokens, v[i - 1].tobytes())
                for i, c in enumerate(chunks, start=1)
            ),
        )
        con.execute("INSERT INTO chunks_fts(chunks_fts) VALUES ('rebuild')")
        con.commit()
    temporal.replace(ruta)
