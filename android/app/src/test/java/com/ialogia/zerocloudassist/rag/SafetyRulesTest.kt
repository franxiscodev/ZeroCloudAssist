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
    fun `cambiar, sustituir o reemplazar el ventilador avisa por la pregunta`() {
        for (question in listOf(
            "¿Cómo cambio el ventilador del variador?",
            "Quiero sustituir el ventilador",
            "¿Cómo reemplazo la placa de control?",
        )) {
            assertEquals(question, expected, SafetyRules.check(question, listOf(neutral)))
        }
    }

    @Test
    fun `avisa si el trozo anterior a un fragmento habla de riesgo`() {
        // B09 en E5: entraron los pasos 4-8 (ids 1271 y 1272) y el aviso estaba en el 1270.
        val warning = Chunk(1270, 372, "Maintenance and hardware diagnostics",
            "WARNING! Read and follow the instructions in chapter Safety on page 17.\n" +
                "1. Stop the drive and disconnect it from the AC power source.", 149)
        val steps = Chunk(1271, 372, "Maintenance and hardware diagnostics",
            "4. Free the fan cable from the clip.\n5. Disconnect the fan cable.", 34)
        val byId = mapOf(warning.id to warning, steps.id to steps)
        assertEquals(expected, SafetyRules.check("¿Qué hago después?", listOf(steps)) { byId[it.id - 1] })
    }

    @Test
    fun `dc bus en el trozo anterior no avisa`() {
        // B04 en E5: el 1228 (F0007) va detrás de una fila de fallos que describe una medida.
        val faultRow = Chunk(1227, 361, "Fault tracing",
            "Measure the input and DC voltage during start, stop and running by using a multimeter " +
                "or check parameter 0107 DC BUS VOLTAGE.", 60)
        val f0007 = Chunk(1228, 361, "Fault tracing",
            "0007 AI1 LOSS\nAnalog input AI1 signal has fallen below limit defined by parameter 3021.", 120)
        assertNull(SafetyRules.check("Aparece F0007 en la pantalla del variador", listOf(f0007)) {
            if (it.id == 1228L) faultRow else null
        })
    }

    @Test
    fun `el trozo anterior sin disparadores no avisa`() {
        val before = chunk("1201 CONST SPEED SEL\nActivates constant speeds.").copy(id = 1)
        val after = neutral.copy(id = 2)
        assertNull(SafetyRules.check("¿Qué hace el parámetro 1202?", listOf(after)) { if (it.id == 2L) before else null })
    }

    @Test
    fun `compara sin tildes ni mayúsculas`() {
        assertEquals(expected, SafetyRules.check("TENSION DEL BUS", emptyList()))
    }
}
