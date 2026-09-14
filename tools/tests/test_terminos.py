"""Tabla de casos de términos literales del plan 02 (§5, E1). La misma tabla va en QueryTermsTest.kt."""

import pytest

from zca_tools.terminos import consulta_fts, terminos_literales

CASOS = [
    ("El variador muestra el fallo F0009", ["0009"], '"0009"'),
    ("¿qué es la alarma A2001?", ["2001"], '"2001"'),
    ("¿Cómo configuro el STO?", ["STO"], '"STO"'),
    ("la entrada DI1 no responde", ["DI1"], '"DI1"'),
    ("¿Para qué sirve el parámetro 9905?", ["9905"], '"9905"'),
    ("fallo f0009 y STO", ["0009", "STO"], '"0009" OR "STO"'),
    ("el motor se calienta mucho", [], None),
    ("Tarda 5 minutos", [], None),
]


@pytest.mark.parametrize(("pregunta", "terminos", "consulta"), CASOS)
def test_tabla_del_plan(pregunta, terminos, consulta):
    assert terminos_literales(pregunta) == terminos
    assert consulta_fts(pregunta) == consulta


def test_sin_duplicados_y_en_orden_de_aparicion():
    assert terminos_literales("STO, F0009 y otra vez 0009 con STO") == ["STO", "0009"]
