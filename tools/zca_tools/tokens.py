"""Contador de tokens de Qwen2.5, para medir los chunks con la misma vara que el prompt del móvil."""

from __future__ import annotations

from collections.abc import Callable
from pathlib import Path

from tokenizers import Tokenizer


def contador_qwen(ruta_tokenizer: Path) -> Callable[[str], int]:
    tokenizador = Tokenizer.from_file(str(ruta_tokenizer))

    def contar(texto: str) -> int:
        return len(tokenizador.encode(texto, add_special_tokens=False).ids)

    return contar
