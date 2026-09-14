"""Tabla de casos de términos literales del plan 02 (§5, E1). La misma tabla va en QueryTermsTest.kt."""

from pathlib import Path

import pytest

from zca_tools.terminos import (
    clase_literal,
    consulta_fts,
    expansiones,
    leer_glosario,
    normalizar_texto,
    palabras_clave,
    terminos_literales,
)

RAIZ = Path(__file__).resolve().parents[2]

GLOSARIO = [
    (("ventilador",), ("fan*",)),
    (("bus de continua",), ('"dc bus"', '"intermediate circuit"')),
    (("calient*", "temperatura"), ("temperature", "overtemp*")),
    (("par",), ("torque",)),
]

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


# --- Glosario taller → manual (tabla de casos gemela en QueryTermsTest.kt) ---


def test_normalizar_texto_quita_tildes_y_mayusculas():
    assert normalizar_texto("¿Cómo MIDO la TENSIÓN?") == "¿como mido la tension?"


@pytest.mark.parametrize(
    ("pregunta", "esperado"),
    [
        ("¿Cómo cambio el ventilador?", ["fan*"]),
        ("El armario está muy CALIENTE", ["temperature", "overtemp*"]),  # prefijo calient*
        ("¿Cómo mido la tensión del bus de continua?", ['"dc bus"', '"intermediate circuit"']),
        ("¿Para qué sirve el parámetro 9905?", []),  # "par" es palabra entera: no casa
        ("sube la temperatura y se calienta", ["temperature", "overtemp*"]),  # sin duplicados
        ("el motor hace un ruido raro", []),
    ],
)
def test_expansiones(pregunta, esperado):
    assert expansiones(pregunta, GLOSARIO) == esperado


def test_consulta_con_literales_y_glosario():
    assert consulta_fts("fallo F0009 y el ventilador", GLOSARIO) == '"0009" OR fan*'
    assert consulta_fts("se calienta el armario", GLOSARIO) == "temperature OR overtemp*"
    assert consulta_fts("el motor hace un ruido raro", GLOSARIO) is None


@pytest.mark.parametrize(
    ("pregunta", "clase"),
    [
        ("El variador muestra el fallo F0009", "codigo"),
        ("¿qué es la alarma A2001?", "codigo"),
        ("Aparece F0007 en la pantalla del variador", "codigo"),
        ("Me sale el error 0016", "codigo"),
        ("¿Para qué sirve el parámetro 9905?", "parametro"),
        ("la entrada DI1 no responde", None),
        ("¿Qué es el 2001?", None),
    ],
)
def test_clase_literal(pregunta, clase):
    assert clase_literal(pregunta) == clase


def test_leer_glosario(tmp_path):
    ruta = tmp_path / "g.yaml"
    ruta.write_text(
        "capitulos:\n  codigo: Fault tracing\n  parametro: Actual signals and parameters\n"
        "terminos:\n  - es: [ventilador]\n    en: [fan*]\n"
        "  - es: [bus de continua]\n    en: ['\"dc bus\"', '\"intermediate circuit\"']\n",
        encoding="utf-8",
    )
    glosario = leer_glosario(ruta)
    assert glosario.capitulos == {"codigo": "Fault tracing", "parametro": "Actual signals and parameters"}
    assert glosario.terminos == [
        (("ventilador",), ("fan*",)),
        (("bus de continua",), ('"dc bus"', '"intermediate circuit"')),
    ]


@pytest.mark.parametrize(
    "entrada",
    ["es: [Ventilador]\n    en: [fan]", "es: [ventilación]\n    en: [fan]", "es: [ventilador]\n    en: [fan OR x]"],
)
def test_leer_glosario_rechaza_entradas_mal_escritas(tmp_path, entrada):
    ruta = tmp_path / "g.yaml"
    ruta.write_text(f"capitulos: {{}}\nterminos:\n  - {entrada}\n", encoding="utf-8")
    with pytest.raises(ValueError):
        leer_glosario(ruta)


@pytest.mark.parametrize(
    ("traduccion", "esperado"),
    [
        ("How do I measure the DC bus voltage?", ["measure", "dc", "bus", "voltage"]),
        ("What does fault F0009 mean and what should I check?", ["fault", "f0009", "mean", "check"]),
        ("The motor runs backwards, the motor!", ["motor", "runs", "backwards"]),  # sin duplicados
        ("", []),
    ],
)
def test_palabras_clave_de_la_traduccion(traduccion, esperado):
    assert palabras_clave(traduccion) == esperado


def test_consulta_con_literales_y_palabras_de_la_traduccion():
    claves = palabras_clave("What does fault F0009 mean?")
    assert consulta_fts("¿Qué es el fallo F0009?", claves=claves) == '"0009" OR "fault" OR "f0009" OR "mean"'


def test_el_glosario_del_repo_es_valido():
    glosario = leer_glosario(RAIZ / "docs" / "glosario-taller.yaml")
    assert set(glosario.capitulos) == {"codigo", "parametro"}
    assert len(glosario.terminos) >= 20
