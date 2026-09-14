"""Convierte intfloat/multilingual-e5-small a GGUF con el conversor del submódulo de llama.cpp.

e5-small declara `BertModel` (cuerpo de Multilingual-MiniLM, posiciones desde 0) pero usa el
tokenizador SentencePiece de XLM-R. La clase `BertModel` del conversor solo lee vocabularios
BPE/WordPiece, y la clase `XLMRobertaModel` recorta las posiciones como en RoBERTa. Este envoltorio
usa `BertModel` con el `set_vocab` de XLM-R que el conversor ya trae, sin tocar el submódulo.

Corre en el entorno del conversor (torch, transformers), no en el de zca-tools. Desde la raíz:

    uv run --no-project --python 3.11 \
      --with-requirements third_party/llama.cpp/requirements/requirements-convert_hf_to_gguf.txt \
      python tools/convertir_e5.py models/hf/multilingual-e5-small \
      --outtype q8_0 --outfile models/multilingual-e5-small-q8_0.gguf

Verificar después con `tools/verificar_e5.py`.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "third_party" / "llama.cpp"))

import convert_hf_to_gguf  # noqa: E402  (añade gguf-py del submódulo al path)
from conversion.base import ModelBase, gguf  # noqa: E402
from conversion.bert import BertModel  # noqa: E402


class BertConVocabXlmr(BertModel):
    model_arch = gguf.MODEL_ARCH.BERT  # el registro exige que cada clase la declare

    def set_vocab(self) -> None:
        self._xlmroberta_set_vocab()


# get_model_class importa conversion.bert con __import__, que ya está en caché: este registro
# prevalece sobre el original.
ModelBase.register("BertModel")(BertConVocabXlmr)

if __name__ == "__main__":
    convert_hf_to_gguf.main()
