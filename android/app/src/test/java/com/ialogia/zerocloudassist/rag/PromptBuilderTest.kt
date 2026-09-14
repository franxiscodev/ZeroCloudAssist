package com.ialogia.zerocloudassist.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Formato exacto del turno del usuario del plan 02, §6. */
class PromptBuilderTest {

    private val chunks = listOf(
        Chunk(id = 1230, page = 362, chapter = "Fault tracing", text = "0009 MOT OVERTEMP\nMotor temperature too high.", tokens = 12),
        Chunk(id = 1227, page = 360, chapter = "Fault tracing", text = "0003 DEV OVERTEMP\nDrive heatsink too hot.", tokens = 11),
    )

    @Test
    fun `turno con dos fragmentos`() {
        assertEquals(
            "Fragmentos del manual (en inglés):\n" +
                "[1] Fault tracing\n0009 MOT OVERTEMP\nMotor temperature too high.\n" +
                "[2] Fault tracing\n0003 DEV OVERTEMP\nDrive heatsink too hot.\n" +
                "\n" +
                "Pregunta: ¿Qué es el fallo F0009?",
            PromptBuilder.userTurn("¿Qué es el fallo F0009?", chunks),
        )
    }

    @Test
    fun `turno sin fragmentos`() {
        assertEquals(
            "Fragmentos del manual (en inglés): (sin fragmentos)\n\nPregunta: ¿Tiene Bluetooth?",
            PromptBuilder.userTurn("¿Tiene Bluetooth?", emptyList()),
        )
    }

    @Test
    fun `nunca escribe números de página`() {
        assertFalse(PromptBuilder.userTurn("¿Qué es el fallo F0009?", chunks).contains("p. "))
        assertFalse(PromptBuilder.SYSTEM_PROMPT.contains("p. "))
    }

    @Test
    fun `prompt de sistema del plan`() {
        assertEquals(
            "Eres un asistente técnico industrial para el variador ABB ACS355. Responde siempre en " +
                "español, de forma breve y con pasos numerados cuando haya que hacer algo. Usa solo los " +
                "fragmentos del manual que acompañan a la pregunta. Si no contienen la respuesta, di: " +
                "\"No aparece en el manual\". No escribas números de página. Si hay riesgo eléctrico, " +
                "avisa primero.",
            PromptBuilder.SYSTEM_PROMPT,
        )
    }
}
