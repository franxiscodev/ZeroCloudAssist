import numpy as np
import pytest
import requests

from zca_tools.vectorizar import normalizar, vectorizar

URL_E5 = "http://127.0.0.1:8090"


def test_normalizar_deja_norma_uno_por_fila():
    v = normalizar(np.array([[3.0, 4.0], [0.0, 2.0]]))
    np.testing.assert_allclose(v, [[0.6, 0.8], [0.0, 1.0]], atol=1e-7)
    assert v.dtype == np.float32


def test_normalizar_un_solo_vector():
    np.testing.assert_allclose(normalizar(np.array([0.0, 3.0, 4.0])), [0.0, 0.6, 0.8], atol=1e-7)


def test_normalizar_vector_nulo_no_da_nan():
    assert not np.isnan(normalizar(np.zeros((1, 3)))).any()


@pytest.mark.manual
def test_contra_llama_server():
    try:
        requests.get(f"{URL_E5}/health", timeout=2)
    except requests.ConnectionError:
        pytest.skip(f"sin llama-server con e5 en {URL_E5}")
    v = vectorizar(["query: el motor se calienta", "passage: Motor overtemperature"], URL_E5)
    assert v.shape == (2, 384)
    assert v.dtype == np.float32
    np.testing.assert_allclose(np.linalg.norm(v, axis=1), 1.0, atol=1e-5)
