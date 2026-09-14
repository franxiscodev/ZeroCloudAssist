"""Términos de la pregunta para la búsqueda FTS5: literales (códigos, parámetros, siglas) y
expansiones del glosario taller → manual.

Gemelo de `rag/QueryTerms.kt`: si cambia una regla, cambia en los dos y en la tabla del plan 02.
Literales: `[FfAa]?\\d{4}` → los 4 dígitos; siglas de 2–4 mayúsculas con hasta 2 dígitos, tal
como aparecen; sin duplicados, en orden de aparición. Glosario: se compara la pregunta sin tildes
y en minúsculas; cada término es palabra entera, o prefijo si acaba en `*`.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

import yaml

_TERMINO = re.compile(r"\b[FfAa]?(\d{4})\b|\b([A-Z]{2,4}\d{0,2}|[A-Z]{1,3}\d{1,2})\b")
_CODIGO = re.compile(r"\b[fa]\d{4}\b|\b(?:fallo|falla|alarma|error|averia|codigo)\b")
_PARAMETRO = re.compile(r"\bparametro")
_PALABRA = re.compile(r"[a-z0-9]+")
_VACIAS_EN = frozenset(
    "a an the and or of to in on for with at by from as into about is are was were be been "
    "do does did done how what which who whom when where why can could should would will shall "
    "may might must i me my we our you your it its this that these those there here have has "
    "had not no yes please if then than so".split()
)
_ES_VALIDO = re.compile(r"[a-z0-9 ]+\*?")
_EN_VALIDO = re.compile(r'"[a-z0-9 -]+"|[a-z0-9-]+\*?')

EntradaGlosario = tuple[tuple[str, ...], tuple[str, ...]]  # (formas en español, expansiones FTS5)


@dataclass(frozen=True)
class Glosario:
    capitulos: dict[str, str]  # "codigo" / "parametro" → capítulo con las entradas que los definen
    terminos: list[EntradaGlosario]


def normalizar_texto(texto: str) -> str:
    sin_tildes = "".join(
        c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn"
    )
    return sin_tildes.lower()


def terminos_literales(pregunta: str) -> list[str]:
    terminos: list[str] = []
    for coincidencia in _TERMINO.finditer(pregunta):
        termino = coincidencia.group(1) or coincidencia.group(2)
        if termino not in terminos:
            terminos.append(termino)
    return terminos


def _casa(forma: str, texto: str) -> bool:
    if forma.endswith("*"):
        return re.search(rf"\b{re.escape(forma[:-1])}", texto) is not None
    return re.search(rf"\b{re.escape(forma)}\b", texto) is not None


def expansiones(pregunta: str, glosario: list[EntradaGlosario]) -> list[str]:
    texto = normalizar_texto(pregunta)
    salida: list[str] = []
    for formas, fts in glosario:
        if any(_casa(forma, texto) for forma in formas):
            salida += [e for e in fts if e not in salida]
    return salida


def palabras_clave(traduccion: str) -> list[str]:
    """Palabras de la pregunta traducida al inglés que merece la pena buscar en FTS5."""
    palabras: list[str] = []
    for palabra in _PALABRA.findall(traduccion.lower()):
        if len(palabra) > 1 and palabra not in _VACIAS_EN and palabra not in palabras:
            palabras.append(palabra)
    return palabras


def consulta_fts(
    pregunta: str, glosario: list[EntradaGlosario] = (), claves: list[str] = ()
) -> str | None:
    partes = [f'"{t}"' for t in terminos_literales(pregunta)] + expansiones(pregunta, glosario)
    partes += [f'"{c}"' for c in claves if f'"{c}"' not in partes]
    return " OR ".join(partes) if partes else None


def clase_literal(pregunta: str) -> str | None:
    """"codigo" si habla de un fallo o alarma, "parametro" si de un parámetro; si no, None."""
    texto = normalizar_texto(pregunta)
    if _CODIGO.search(texto):
        return "codigo"
    if _PARAMETRO.search(texto):
        return "parametro"
    return None


def leer_glosario(ruta: Path) -> Glosario:
    datos = yaml.safe_load(Path(ruta).read_text(encoding="utf-8"))
    terminos: list[EntradaGlosario] = []
    for entrada in datos.get("terminos") or []:
        es, en = tuple(entrada["es"]), tuple(entrada["en"])
        for forma in es:
            if not _ES_VALIDO.fullmatch(forma):
                raise ValueError(f"glosario: {forma!r} debe ir en minúsculas y sin tildes")
        for fts in en:
            if not _EN_VALIDO.fullmatch(fts):
                raise ValueError(f"glosario: {fts!r} no es una palabra, prefijo o frase FTS5")
        terminos.append((es, en))
    return Glosario(dict(datos.get("capitulos") or {}), terminos)
