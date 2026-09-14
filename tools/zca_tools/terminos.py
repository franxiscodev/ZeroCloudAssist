"""Términos literales de la pregunta (códigos, parámetros, siglas) para la búsqueda FTS5.

Gemelo de `rag/QueryTerms.kt`: si cambia la regla, cambia en los dos y en la tabla del plan 02.
Regla: `[FfAa]?\\d{4}` → los 4 dígitos; siglas de 2–4 mayúsculas con hasta 2 dígitos, tal como
aparecen; sin duplicados, en orden de aparición.
"""

from __future__ import annotations

import re

_TERMINO = re.compile(r"\b[FfAa]?(\d{4})\b|\b([A-Z]{2,4}\d{0,2}|[A-Z]{1,3}\d{1,2})\b")


def terminos_literales(pregunta: str) -> list[str]:
    terminos: list[str] = []
    for coincidencia in _TERMINO.finditer(pregunta):
        termino = coincidencia.group(1) or coincidencia.group(2)
        if termino not in terminos:
            terminos.append(termino)
    return terminos


def consulta_fts(pregunta: str) -> str | None:
    terminos = terminos_literales(pregunta)
    return " OR ".join(f'"{t}"' for t in terminos) if terminos else None
