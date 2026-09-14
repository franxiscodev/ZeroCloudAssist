import pytest
from pypdf import PdfReader

from conftest import PDF_MANUAL, TOKENIZER_QWEN, requiere
from zca_tools.tokens import contador_qwen


@pytest.mark.manual
def test_cuenta_sin_tokens_especiales():
    requiere(TOKENIZER_QWEN)
    contar = contador_qwen(TOKENIZER_QWEN)
    assert contar("") == 0
    assert contar("Hello world") == 2


@pytest.mark.manual
def test_capitulo_fault_tracing_cuadra_con_llama_tokenize():
    """Páginas 351–370: 8 863 tokens con llama-tokenize b10941 y el GGUF de Qwen (plan 02, §1).

    Sobre el texto en bruto de pypdf, que es lo que se midió; el limpio no lleva las cabeceras.
    """
    requiere(TOKENIZER_QWEN)
    requiere(PDF_MANUAL)
    contar = contador_qwen(TOKENIZER_QWEN)
    paginas = PdfReader(PDF_MANUAL).pages[350:370]
    texto = "\n".join(p.extract_text() for p in paginas)
    assert contar(texto) == pytest.approx(8863, rel=0.02)
