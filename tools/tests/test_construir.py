import sqlite3
from contextlib import closing

import numpy as np
import pytest

from zca_tools.construir import CLAVES_META, construir
from zca_tools.trocear import Chunk
from zca_tools.vectorizar import normalizar

CHUNKS = [
    Chunk(360, "Fault tracing", "0003 DEV OVERTEMP\nDrive IGBT temperature is excessive.", 14),
    Chunk(362, "Fault tracing", "0009 MOT OVERTEMP\nMotor temperature estimation is too high.", 13),
    Chunk(18, "Safety", "Wait for 5 minutes to let the capacitors discharge.", 12),
]
META = {
    "esquema": "1",
    "manual": "ABB ACS355 User's manual",
    "documento": "EN_ACS355_UM_E_A5.pdf",
    "version": "2026-09-14.1",
    "embeddings": "multilingual-e5-small-q8_0",
    "embeddings_sha256": "0" * 64,
    "dimension": "384",
    "tokens_objetivo": "130",
}


def vectores(n: int = 3) -> np.ndarray:
    return normalizar(np.random.default_rng(0).standard_normal((n, 384)))


def consultar(ruta, sql):
    with closing(sqlite3.connect(ruta)) as con:
        return con.execute(sql).fetchall()


def test_fts5_encuentra_el_codigo(tmp_path):
    ruta = tmp_path / "manual.sqlite"
    construir(ruta, CHUNKS, vectores(), META)
    filas = consultar(ruta, "SELECT c.id, c.pagina FROM chunks_fts f JOIN chunks c ON c.id = f.rowid "
                            "WHERE chunks_fts MATCH '\"0009\"'")
    assert filas == [(2, 362)]


def test_el_blob_devuelve_los_mismos_384_floats(tmp_path):
    ruta = tmp_path / "manual.sqlite"
    v = vectores()
    construir(ruta, CHUNKS, v, META)
    [(blob,)] = consultar(ruta, "SELECT vector FROM chunks WHERE id = 2")
    assert len(blob) == 384 * 4
    np.testing.assert_array_equal(np.frombuffer(blob, dtype="<f4"), v[1])


def test_meta_y_columnas(tmp_path):
    ruta = tmp_path / "manual.sqlite"
    construir(ruta, CHUNKS, vectores(), META)
    assert dict(consultar(ruta, "SELECT clave, valor FROM meta")) == META
    assert consultar(ruta, "SELECT pagina, capitulo, tokens FROM chunks WHERE id = 3") == [
        (18, "Safety", 12)
    ]


def test_reconstruir_sustituye_el_fichero(tmp_path):
    ruta = tmp_path / "manual.sqlite"
    construir(ruta, CHUNKS, vectores(), META)
    construir(ruta, CHUNKS[:1], vectores(1), META)
    assert consultar(ruta, "SELECT count(*) FROM chunks") == [(1,)]


def test_guarda_el_glosario(tmp_path):
    ruta = tmp_path / "manual.sqlite"
    glosario = [(("ventilador",), ("fan*",)),
                (("bus de continua", "tension continua"), ('"dc bus"', '"intermediate circuit"'))]
    construir(ruta, CHUNKS, vectores(), META, glosario)
    assert consultar(ruta, "SELECT es, en FROM glosario ORDER BY rowid") == [
        ("ventilador", "fan*"),
        ("bus de continua|tension continua", '"dc bus"|"intermediate circuit"'),
    ]


def test_rechaza_meta_incompleta(tmp_path):
    incompleta = {k: v for k, v in META.items() if k != "version"}
    with pytest.raises(ValueError, match="version"):
        construir(tmp_path / "m.sqlite", CHUNKS, vectores(), incompleta)


def test_rechaza_vectores_que_no_cuadran(tmp_path):
    with pytest.raises(ValueError):
        construir(tmp_path / "m.sqlite", CHUNKS, vectores(2), META)


def test_claves_meta_del_plan():
    assert set(CLAVES_META) == set(META)
