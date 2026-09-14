package com.ialogia.zerocloudassist.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Casos de `SafetyRules` del plan 02, §5 (E3). */
class SafetyRulesTest {

    private fun chunk(text: String) = Chunk(id = 1, page = 200, chapter = "Actual signals and parameters", text = text, tokens = 10)

    private val neutral = chunk("1202 CONST SPEED 1\nDefines constant speed 1.")
    private val expected = SafetyNotice(SafetyRules.NOTICE, 18)

    @Test
    fun `medir la tensión del bus de continua avisa con la página 18`() {
        assertEquals(expected, SafetyRules.check("¿Cómo mido la tensión del bus de continua?", listOf(neutral)))
    }

    @Test
    fun `desmontar el cable del motor avisa`() {
        assertEquals(expected, SafetyRules.check("Voy a desmontar el cable del motor", emptyList()))
    }

    @Test
    fun `una pregunta de parámetro con fragmentos sin disparadores no avisa`() {
        assertNull(SafetyRules.check("¿Qué hace el parámetro 1202?", listOf(neutral)))
    }

    @Test
    fun `avisa si un fragmento habla de riesgo aunque la pregunta sea neutra`() {
        for (text in listOf(
            "Wait until the capacitors discharge.",
            "Never work on the drive while input power is applied.",
            "Before replacing the fan, disconnect it from the AC power source.",
        )) {
            assertEquals(text, expected, SafetyRules.check("¿Cómo cambio el ventilador?", listOf(neutral, chunk(text))))
        }
    }

    @Test
    fun `compara sin tildes ni mayúsculas`() {
        assertEquals(expected, SafetyRules.check("TENSION DEL BUS", emptyList()))
    }
}
