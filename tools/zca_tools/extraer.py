"""PDF del manual → páginas limpias con su capítulo.

La página del PDF coincide con la impresa (plan 02, §1). Cada página empieza con la cabecera
"358   Fault tracing" (pares), "Fault tracing   359" (impares) o solo el número (índice). pypdf
deja al final de la página la cabecera de columnas de las tablas, que se repite en cada página de
la tabla y no aporta nada al buscar.
"""

from __future__ import annotations

import bisect
import re
from dataclasses import dataclass
from pathlib import Path

from pypdf import PdfReader

CABECERA_DE_TABLA = re.compile(
    r"(?:ALARM )?CODE (?:FAULT |ALARM )?CAUSE WHAT TO DO|No\. Name/Value Description (?:Def/)?FbEq"
)
_ETIQUETAS_DE_TABLA = {"All parameters", "Actual signals"}
_USO_PRIVADO = re.compile("[-]")  # símbolos de la fuente de iconos (p. ej. U+F06E)


@dataclass(frozen=True)
class Pagina:
    numero: int  # página impresa = página del PDF
    capitulo: str  # del índice del PDF; "" en portada, índice y contraportada
    texto: str  # sin cabecera ni pie


def _es_cabecera(linea: str, numero: int) -> bool:
    n = str(numero)
    return bool(re.fullmatch(rf"{n}(?:\s{{2,}}.+)?|.+\S\s{{2,}}{n}", linea))


def _lineas(texto: str) -> list[str]:
    lineas = (_USO_PRIVADO.sub("", linea).strip() for linea in texto.splitlines())
    return [linea for linea in lineas if linea]


def limpiar_cabecera(texto: str, numero: int) -> str:
    lineas = _lineas(texto)
    if lineas and _es_cabecera(lineas[0], numero):
        lineas = lineas[1:]
    if lineas and CABECERA_DE_TABLA.fullmatch(lineas[-1]):
        lineas = lineas[:-1]
        if lineas and lineas[-1] in _ETIQUETAS_DE_TABLA:
            lineas = lineas[:-1]
    return "\n".join(lineas)


def capitulo_de(numero: int, inicios: list[tuple[int, str]]) -> str:
    """Capítulo que contiene la página, con `inicios` = [(primera página, título)] ordenados."""
    i = bisect.bisect_right([pagina for pagina, _ in inicios], numero)
    return inicios[i - 1][1] if i else ""


def leer_manual(ruta: Path) -> list[Pagina]:
    lector = PdfReader(ruta)
    inicios = sorted(
        (lector.get_destination_page_number(entrada) + 1, entrada.title)
        for entrada in lector.outline
        if not isinstance(entrada, list)  # las listas son subapartados
    )
    paginas = []
    for numero, pagina in enumerate(lector.pages, start=1):
        bruto = pagina.extract_text()
        lineas = _lineas(bruto)
        # Sin cabecera numerada = portada o contraportada: sin capítulo, no se indexa.
        numerada = bool(lineas) and _es_cabecera(lineas[0], numero)
        capitulo = capitulo_de(numero, inicios) if numerada else ""
        paginas.append(Pagina(numero, capitulo, limpiar_cabecera(bruto, numero)))
    return paginas
