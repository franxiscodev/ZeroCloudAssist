"""Traducción de la pregunta al inglés con Qwen, como la hará el móvil antes de buscar.

Mismo prompt de sistema que la respuesta (§6 del plan 02, gemelo en `rag/PromptBuilder.kt`): en
el móvil queda en la caché KV y la traducción es un turno más. Cliente de `/v1/chat/completions`
de llama-server b10941 (compatible con OpenAI).
"""

from __future__ import annotations

import re

import requests

SYSTEM_PROMPT = (
    "Eres un asistente técnico industrial para el variador ABB ACS355. Responde siempre en "
    "español, de forma breve y con pasos numerados cuando haya que hacer algo. Usa solo los "
    "fragmentos del manual que acompañan a la pregunta. Si no contienen la respuesta, di: "
    '"No aparece en el manual". No escribas números de página. Si hay riesgo eléctrico, '
    "avisa primero."
)
PETICION = (
    "Translate this question into English to search the manual. Keep codes and parameter "
    "numbers as they are. Reply with the translation only.\n"
)
_ETIQUETA = re.compile(r"^(?:translation|english)\s*:\s*", re.IGNORECASE)


def mensajes(pregunta: str) -> list[dict[str, str]]:
    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": PETICION + pregunta},
    ]


def limpiar(salida: str) -> str:
    """Primera línea con texto, sin etiqueta ("Translation:") ni comillas alrededor."""
    linea = next((l.strip() for l in salida.splitlines() if l.strip()), "")
    return _ETIQUETA.sub("", linea).strip().strip("\"'“”").strip()


def traducir(pregunta: str, url: str, temperatura: float = 0.2, max_tokens: int = 64) -> dict:
    """{"en": traducción, "tokens_prompt": …, "tokens_respuesta": …}. Temperatura 0,2 como el móvil."""
    respuesta = requests.post(
        f"{url}/v1/chat/completions",
        json={"messages": mensajes(pregunta), "temperature": temperatura, "seed": 42,
              "max_tokens": max_tokens},
        timeout=120,
    )
    respuesta.raise_for_status()
    datos = respuesta.json()
    return {
        "en": limpiar(datos["choices"][0]["message"]["content"]),
        "tokens_prompt": datos["usage"]["prompt_tokens"],
        "tokens_respuesta": datos["usage"]["completion_tokens"],
    }
