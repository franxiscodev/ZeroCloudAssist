"""Tablas de RRF y presupuesto del plan 02 (§5, E1). La misma tabla va en ManualSearchTest.kt."""

from zca_tools.evaluar import acierta, rrf, seleccionar


def test_rrf_fusiona_por_rango():
    # 1: 1/61 + 1/62; 3: 1/63 + 1/61; 2: 1/62
    assert rrf([[1, 2, 3], [3, 1]]) == [1, 3, 2]


def test_rrf_con_una_lista_vacia():
    assert rrf([[], [5, 4]]) == [5, 4]


def test_seleccionar_llena_el_presupuesto_en_orden():
    # 130 + 8 + 140 + 8 = 286 ≤ 300
    assert seleccionar([1, 2, 3], {1: 130, 2: 140, 3: 120}) == [1, 2]


def test_seleccionar_salta_el_que_no_cabe_y_sigue():
    assert seleccionar([1, 2, 3], {1: 200, 2: 150, 3: 80}) == [1, 3]


def test_seleccionar_descarta_un_chunk_que_no_cabe_solo():
    assert seleccionar([1], {1: 320}) == []


def test_acierta_si_alguna_pagina_esperada_esta_entre_las_devueltas():
    assert acierta([360, 362], [362])
    assert acierta([18, 183], [183, 18])
    assert not acierta([359, 361], [362])
    assert not acierta([], [362])
