"""Casos de `trocear` del plan 02 (§5, E1), con textos sintéticos y contador por palabras."""

from zca_tools.extraer import Pagina
from zca_tools.trocear import trocear


def contar(texto: str) -> int:
    return len(texto.split())


def frases(n_palabras: int, prefijo: str = "w", por_frase: int = 10) -> str:
    """Texto de `n_palabras` en frases de `por_frase` palabras que terminan en punto."""
    palabras = [f"{prefijo}{i}" for i in range(n_palabras)]
    trozos = [palabras[i : i + por_frase] for i in range(0, n_palabras, por_frase)]
    return " ".join(" ".join(t) + "." for t in trozos)


def entrada(codigo: str, nombre: str, n_palabras: int) -> str:
    # Imita una fila de la tabla de fallos: código y nombre, causa y qué hacer, en varias líneas.
    cuerpo = frases(n_palabras - 2, prefijo=f"c{codigo}x", por_frase=8)
    return f"{codigo} {nombre}\n" + cuerpo.replace(". ", ".\n")


def test_entradas_de_fallo_no_se_parten():
    entradas = [entrada("0001", "OVERCURRENT", 40), entrada("0002", "DC OVERVOLT", 40),
                entrada("0003", "DEV OVERTEMP", 40)]
    chunks = trocear([Pagina(360, "Fault tracing", "\n".join(entradas))], contar)
    assert 1 <= len(chunks) <= 3
    for e in entradas:
        assert sum(e in c.texto for c in chunks) == 1, "cada entrada entera en un solo chunk"


def test_linea_que_continua_la_frase_no_abre_bloque():
    # Como en la p. 200: la línea empieza por un número de parámetro pero sigue la frase anterior.
    primera = ("0101 TIMED FUNC\nExternal speed reference, speed defined by parameter\n"
               "1202 CONST SPEED 1 or speed defined by parameter\n"
               "1203 CONST SPEED 2 is used.")
    segunda = "0102 OTHER FUNC\nSee selection TIMED FUNC."
    # maximo = 26: la primera entrada (25 palabras) cabe entera y no se une con la segunda (7).
    chunks = trocear([Pagina(200, "Parameters", primera + "\n" + segunda)], contar,
                     objetivo=5, maximo=26)
    assert [c.texto for c in chunks] == [primera, segunda]


def test_dos_parrafos_cortos_van_juntos():
    texto = frases(60, "a") + "\n" + frases(60, "b")
    chunks = trocear([Pagina(30, "Intro", texto)], contar)
    assert len(chunks) == 1
    assert chunks[0].tokens == 120


def test_bloque_largo_se_parte_por_frases():
    texto = frases(400)
    chunks = trocear([Pagina(40, "Intro", texto)], contar)
    assert len(chunks) > 1
    assert all(c.tokens <= 170 for c in chunks)
    assert all(c.texto.endswith(".") for c in chunks), "se corta en fin de frase"
    assert " ".join(c.texto for c in chunks).split() == texto.split(), "no se pierde texto"


def test_entrada_larga_repite_su_cabecera_en_cada_trozo():
    texto = entrada("0009", "MOT OVERTEMP", 300)
    chunks = trocear([Pagina(362, "Fault tracing", texto)], contar)
    assert len(chunks) > 1
    assert all(c.texto.startswith("0009 MOT OVERTEMP\n") for c in chunks)
    assert all(c.tokens <= 170 for c in chunks)


def test_nunca_mezcla_dos_paginas():
    paginas = [Pagina(10, "Intro", frases(30, "p10x")), Pagina(11, "Intro", frases(30, "p11x"))]
    chunks = trocear(paginas, contar)
    assert [c.pagina for c in chunks] == [10, 11]
    assert "p11x" not in chunks[0].texto


def test_pagina_vacia_no_da_chunks():
    assert trocear([Pagina(12, "Intro", "")], contar) == []


def test_pagina_sin_capitulo_no_se_indexa():
    assert trocear([Pagina(5, "", frases(50))], contar) == []


def test_conserva_capitulo_y_cuenta_tokens():
    [chunk] = trocear([Pagina(18, "Safety", frases(20))], contar)
    assert (chunk.pagina, chunk.capitulo, chunk.tokens) == (18, "Safety", 20)
