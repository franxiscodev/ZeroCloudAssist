"""Traducción de la pregunta con Qwen, igual que la hará el móvil (gemelo en PromptBuilder.kt)."""

import pytest

from zca_tools.traducir import SYSTEM_PROMPT, SYSTEM_TRADUCTOR, limpiar, mensajes


def test_prompt_de_sistema_es_el_del_plan():
    assert SYSTEM_PROMPT == (
        "Eres un asistente técnico industrial para el variador ABB ACS355. Responde siempre en "
        "español, de forma breve y con pasos numerados cuando haya que hacer algo. Usa solo los "
        "fragmentos del manual que acompañan a la pregunta. Si no contienen la respuesta, di: "
        '"No aparece en el manual". No escribas números de página. Si hay riesgo eléctrico, '
        "avisa primero."
    )


def test_mensajes_de_la_traduccion():
    assert mensajes("¿Cómo mido la tensión del bus de continua?") == [
        {"role": "system", "content": SYSTEM_PROMPT},
        {
            "role": "user",
            "content": "Translate this question into English to search the manual. Keep codes and "
            "parameter numbers as they are. Reply with the translation only.\n"
            "¿Cómo mido la tensión del bus de continua?",
        },
    ]


def test_mensajes_t1_declaran_la_excepcion_al_idioma():
    assert mensajes("¿Cómo cambio el ventilador?", "t1") == [
        {"role": "system", "content": SYSTEM_PROMPT},
        {
            "role": "user",
            "content": "Tarea especial, excepción a la norma de responder en español: traduce al "
            "inglés la pregunta siguiente, sin responderla. Mantén los códigos y los números de "
            "parámetro. Escribe solo la traducción en inglés.\n"
            "Pregunta: ¿Cómo cambio el ventilador?",
        },
    ]


def test_mensajes_t2_con_sistema_de_traductor():
    assert mensajes("¿Cómo cambio el ventilador?", "t2") == [
        {"role": "system", "content": SYSTEM_TRADUCTOR},
        {"role": "user", "content": "¿Cómo cambio el ventilador?"},
    ]
    assert "never answer" in SYSTEM_TRADUCTOR


@pytest.mark.parametrize(
    ("salida", "esperado"),
    [
        ('  "How do I measure the DC bus voltage?"  ', "How do I measure the DC bus voltage?"),
        ("Translation: What does fault F0009 mean?", "What does fault F0009 mean?"),
        ("What is alarm A2001?\n\n(Note: A2001 is an alarm code.)", "What is alarm A2001?"),
        ("\n\nThe motor runs backwards.", "The motor runs backwards."),
        ("", ""),
    ],
)
def test_limpiar_se_queda_con_la_traduccion(salida, esperado):
    assert limpiar(salida) == esperado
