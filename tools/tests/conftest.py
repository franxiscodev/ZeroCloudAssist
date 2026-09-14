from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parents[2]
PDF_MANUAL = RAIZ / "manuales" / "EN_ACS355_UM_E_A5.pdf"
TOKENIZER_QWEN = RAIZ / "models" / "qwen2.5-tokenizer.json"


def requiere(ruta: Path) -> None:
    """Salta el test si falta un fichero que solo existe en el PC de desarrollo."""
    if not ruta.exists():
        pytest.skip(f"falta {ruta.relative_to(RAIZ)}")
