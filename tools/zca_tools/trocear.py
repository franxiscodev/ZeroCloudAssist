"""Páginas → chunks de ~130 tokens de Qwen cortando por la estructura del manual.

Un bloque es una entrada de fallo o de parámetro (línea `NNNN NOMBRE`) o la prosa entre dos
entradas. Los bloques que caben se agrupan hasta el objetivo sin partirse; los que no caben se
parten por frases y, si son una entrada, cada trozo repite su cabecera para que se encuentre por
el código. Nunca se cruza una página.
"""

from __future__ import annotations

import re
from collections.abc import Callable
from dataclasses import dataclass

from zca_tools.extraer import CABECERA_DE_TABLA, Pagina

_ENTRADA = re.compile(r"\d{4} [A-Z]")
_FIN_DE_UNIDAD = re.compile(r"[.!?:)]$|\d$")  # fin de frase, o fila de tabla que acaba en su valor
_ENTRE_FRASES = re.compile(r"(?<=[.!?:])\s+(?=\S)")


@dataclass(frozen=True)
class Chunk:
    pagina: int
    capitulo: str
    texto: str
    tokens: int  # tokens de Qwen


def _bloques(lineas: list[str]) -> list[list[str]]:
    bloques: list[list[str]] = []
    for linea in lineas:
        # Una línea `NNNN NOMBRE` solo abre bloque si la anterior cierra: si no, continúa su frase
        # ("…defined by parameter\n1202 CONST SPEED 1 or…", p. 200).
        anterior = bloques[-1][-1] if bloques else ""
        abre = _ENTRADA.match(linea) and (
            not anterior or _FIN_DE_UNIDAD.search(anterior) or CABECERA_DE_TABLA.fullmatch(anterior)
        )
        if abre or not bloques:
            bloques.append([linea])
        else:
            bloques[-1].append(linea)
    return bloques


def _unidades(lineas: list[str]) -> list[str]:
    """Frases (o filas de tabla). Cada unidad lleva detrás su separador original (espacio o salto
    de línea), para que al volver a juntarlas el texto quede como estaba."""
    unidades, actual = [], ""
    for linea in lineas:
        trozos = _ENTRE_FRASES.split(linea)
        for i, trozo in enumerate(trozos):
            actual += trozo + (" " if i < len(trozos) - 1 else "\n")
            if _FIN_DE_UNIDAD.search(trozo):
                unidades.append(actual)
                actual = ""
    if actual:
        unidades.append(actual)
    return unidades


def _por_palabras(unidad: str, cabe: Callable[[str], bool]) -> list[str]:
    """Último recurso para una frase que no cabe sola: se corta entre palabras."""
    trozos, actual = [], ""
    for palabra in unidad.split(" "):
        if actual and not cabe(actual + palabra):
            trozos.append(actual)
            actual = ""
        actual += palabra + " "
    return trozos + [actual] if actual else trozos


def _empaquetar(
    unidades: list[str], contar: Callable[[str], int], objetivo: int, maximo: int,
    cabecera: str = "",
) -> list[str]:
    """Junta unidades (con su separador detrás) en piezas de ~objetivo y nunca más de maximo."""

    def pieza(texto: str) -> str:
        return f"{cabecera}\n{texto.strip()}" if cabecera else texto.strip()

    def cabe(texto: str) -> bool:
        return contar(pieza(texto)) <= maximo

    piezas, actual = [], ""
    for unidad in unidades:
        for trozo in [unidad] if cabe(unidad) else _por_palabras(unidad, cabe):
            if actual and (contar(pieza(actual)) >= objetivo or not cabe(actual + trozo)):
                piezas.append(actual)
                actual = ""
            actual += trozo
    if actual:
        piezas.append(actual)
    # Una cola corta se une a la pieza anterior si cabe.
    if len(piezas) > 1 and cabe(piezas[-2] + piezas[-1]):
        piezas[-2:] = [piezas[-2] + piezas[-1]]
    return [pieza(p) for p in piezas]


def trocear(
    paginas: list[Pagina], contar: Callable[[str], int], objetivo: int = 130, maximo: int = 170
) -> list[Chunk]:
    chunks = []
    for pagina in paginas:
        if not pagina.capitulo:  # portada, índice y contraportada
            continue
        textos: list[str] = []
        pendientes: list[str] = []  # bloques que caben enteros, a la espera de agruparse
        for bloque in _bloques(pagina.texto.splitlines()):
            texto = "\n".join(bloque)
            if contar(texto) <= maximo:
                pendientes.append(texto + "\n")
                continue
            textos += _empaquetar(pendientes, contar, objetivo, maximo)
            pendientes = []
            cabecera, cuerpo = (bloque[0], bloque[1:]) if _ENTRADA.match(bloque[0]) else ("", bloque)
            textos += _empaquetar(_unidades(cuerpo), contar, objetivo, maximo, cabecera)
        textos += _empaquetar(pendientes, contar, objetivo, maximo)
        chunks += [Chunk(pagina.numero, pagina.capitulo, t, contar(t)) for t in textos]
    return chunks
