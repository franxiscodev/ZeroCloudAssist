import pytest

from conftest import PDF_MANUAL, requiere
from zca_tools.extraer import capitulo_de, leer_manual, limpiar_cabecera


def test_quita_cabecera_de_pagina_par():
    texto = "358   Fault tracing\n5087 Parameter download has failed.\nCheck the drive."
    assert limpiar_cabecera(texto, 358) == "5087 Parameter download has failed.\nCheck the drive."


def test_quita_cabecera_de_pagina_impar():
    assert limpiar_cabecera("Fault tracing   359\nscrew from the drive.", 359) == (
        "screw from the drive."
    )


def test_quita_cabecera_que_solo_es_el_numero():
    assert limpiar_cabecera("6\nTable of contents", 6) == "Table of contents"


def test_no_quita_una_primera_linea_que_no_es_la_cabecera():
    texto = "0009 MOT OVERTEMP\nMotor temperature estimation is too high."
    assert limpiar_cabecera(texto, 362) == texto


def test_no_confunde_la_cabecera_de_otra_pagina():
    texto = "358   Fault tracing\n5087 Parameter download has failed."
    assert limpiar_cabecera(texto, 359) == texto


@pytest.mark.parametrize(
    "pie",
    [
        "CODE FAULT CAUSE WHAT TO DO",
        "ALARM CODE CAUSE WHAT TO DO",
        "CODE ALARM CAUSE WHAT TO DO",  # tabla de alarmas, p. 353
        "All parameters\nNo. Name/Value Description Def/FbEq",
        "Actual signals\nNo. Name/Value Description FbEq",
    ],
)
def test_quita_la_cabecera_de_tabla_repetida_al_pie(pie):
    texto = f"200   Actual signals and parameters\n1 1 No constant speed\n{pie}"
    assert limpiar_cabecera(texto, 200) == "1 1 No constant speed"


def test_conserva_las_lineas_de_tabla():
    texto = (
        "201   Actual signals and parameters\n"
        "DI1 DI2 Operation\n"
        "1 1 No constant speed\n"
        "0 1 Speed defined by par. 1202 CONST SPEED 1"
    )
    assert limpiar_cabecera(texto, 201).splitlines() == [
        "DI1 DI2 Operation",
        "1 1 No constant speed",
        "0 1 Speed defined by par. 1202 CONST SPEED 1",
    ]


def test_quita_simbolos_de_uso_privado_y_lineas_vacias():
    texto = "76   Control panels\n Settings   \n\nPress the key."
    assert limpiar_cabecera(texto, 76) == "Settings\nPress the key."


def test_capitulo_de_usa_el_inicio_mas_cercano_por_debajo():
    inicios = [(17, "Safety"), (21, "Introduction to the manual"), (351, "Fault tracing")]
    assert capitulo_de(16, inicios) == ""
    assert capitulo_de(17, inicios) == "Safety"
    assert capitulo_de(20, inicios) == "Safety"
    assert capitulo_de(362, inicios) == "Fault tracing"


@pytest.mark.manual
def test_manual_real():
    requiere(PDF_MANUAL)
    paginas = leer_manual(PDF_MANUAL)
    assert len(paginas) == 440
    assert [p.numero for p in paginas] == list(range(1, 441))
    p362 = paginas[361]
    assert p362.capitulo == "Fault tracing"
    assert p362.texto.startswith("0009 MOT OVERTEMP")
    assert paginas[17].capitulo == "Safety"  # p. 18, la regla de los 5 minutos
    # Portada, índice y contraportada no tienen capítulo: no se indexan.
    assert paginas[4].capitulo == ""
    assert paginas[439].capitulo == ""
