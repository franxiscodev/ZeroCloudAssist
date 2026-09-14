"""Fusión RRF, presupuesto de tokens y acierto por página (gemelo de `rag/ManualSearch.kt`).

Si cambia una regla, cambia en los dos y en la tabla del plan 02. Empates de RRF: gana el que
apareció antes (orden estable).
"""

from __future__ import annotations

import re


def priorizar(
    ids: list[int], textos: dict[int, str], capitulos: dict[int, str],
    terminos: list[str], capitulo_preferido: str | None,
) -> list[int]:
    """Reordena los resultados de FTS5: primero los chunks con una línea que empieza por un término
    literal (la entrada que lo define, no una mención), y de esos, primero los del capítulo
    preferido. Dentro de cada grupo se conserva el orden de bm25."""
    if not terminos:
        return list(ids)
    entradas = [re.compile(rf"(?m)^{re.escape(t)}(?:[ \t]|$)") for t in terminos]

    def grupo(id_: int) -> int:
        if not any(e.search(textos[id_]) for e in entradas):
            return 2
        return 0 if capitulo_preferido and capitulos[id_] == capitulo_preferido else 1

    return sorted(ids, key=grupo)


def rrf(rankings: list[list[int]], k: int = 60) -> list[int]:
    puntos: dict[int, float] = {}
    for ranking in rankings:
        for rango, id_ in enumerate(ranking, start=1):
            puntos[id_] = puntos.get(id_, 0.0) + 1.0 / (k + rango)
    return sorted(puntos, key=lambda id_: -puntos[id_])


def seleccionar(
    ids: list[int], tokens: dict[int, int],
    max_tokens: int = 300, max_chunks: int = 2, cabecera: int = 8,
) -> list[int]:
    """Los primeros chunks que caben en el presupuesto; el que no cabe se salta y se sigue."""
    elegidos, usados = [], 0
    for id_ in ids:
        if len(elegidos) == max_chunks:
            break
        coste = tokens[id_] + cabecera
        if usados + coste <= max_tokens:
            elegidos.append(id_)
            usados += coste
    return elegidos


def acierta(paginas_devueltas: list[int], paginas_esperadas: list[int]) -> bool:
    return bool(set(paginas_devueltas) & set(paginas_esperadas))
