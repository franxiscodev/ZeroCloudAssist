package com.ialogia.zerocloudassist.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tabla de casos del plan 02 (§5, E1), gemela de `tools/tests/test_terminos.py`. */
class QueryTermsTest {

    private val glossary = listOf(
        GlossaryEntry(listOf("ventilador"), listOf("fan*")),
        GlossaryEntry(listOf("bus de continua"), listOf("\"dc bus\"", "\"intermediate circuit\"")),
        GlossaryEntry(listOf("calient*", "temperatura"), listOf("temperature", "overtemp*")),
        GlossaryEntry(listOf("par"), listOf("torque")),
    )

    private val planCases = listOf(
        Triple("El variador muestra el fallo F0009", listOf("0009"), "\"0009\""),
        Triple("¿qué es la alarma A2001?", listOf("2001"), "\"2001\""),
        Triple("¿Cómo configuro el STO?", listOf("STO"), "\"STO\""),
        Triple("la entrada DI1 no responde", listOf("DI1"), "\"DI1\""),
        Triple("¿Para qué sirve el parámetro 9905?", listOf("9905"), "\"9905\""),
        Triple("fallo f0009 y STO", listOf("0009", "STO"), "\"0009\" OR \"STO\""),
        Triple("el motor se calienta mucho", emptyList(), null),
        Triple("Tarda 5 minutos", emptyList(), null),
    )

    @Test
    fun `tabla del plan`() {
        for ((question, literals, query) in planCases) {
            assertEquals(question, literals, QueryTerms.literals(question))
            assertEquals(question, query, QueryTerms.ftsQuery(question))
        }
    }

    @Test
    fun `sin duplicados y en orden de aparición`() {
        assertEquals(listOf("STO", "0009"), QueryTerms.literals("STO, F0009 y otra vez 0009 con STO"))
    }

    @Test
    fun `normalizar quita tildes y mayúsculas`() {
        assertEquals("¿como mido la tension?", QueryTerms.normalize("¿Cómo MIDO la TENSIÓN?"))
    }

    @Test
    fun `expansiones del glosario`() {
        val cases = listOf(
            "¿Cómo cambio el ventilador?" to listOf("fan*"),
            "El armario está muy CALIENTE" to listOf("temperature", "overtemp*"),  // prefijo calient*
            "¿Cómo mido la tensión del bus de continua?" to listOf("\"dc bus\"", "\"intermediate circuit\""),
            "¿Para qué sirve el parámetro 9905?" to emptyList(),  // "par" es palabra entera: no casa
            "sube la temperatura y se calienta" to listOf("temperature", "overtemp*"),  // sin duplicados
            "el motor hace un ruido raro" to emptyList(),
        )
        for ((question, expected) in cases) {
            assertEquals(question, expected, QueryTerms.expansions(question, glossary))
        }
    }

    @Test
    fun `una palabra entera no casa dentro de otra con tilde`() {
        // Sin normalizar, "par" va seguido de "á": en Python (\b Unicode) no hay frontera de palabra.
        assertEquals(emptyList<String>(), QueryTerms.expansions("PARÁMETRO", glossary))
    }

    @Test
    fun `consulta con literales y glosario`() {
        assertEquals("\"0009\" OR fan*", QueryTerms.ftsQuery("fallo F0009 y el ventilador", glossary))
        assertEquals("temperature OR overtemp*", QueryTerms.ftsQuery("se calienta el armario", glossary))
        assertNull(QueryTerms.ftsQuery("el motor hace un ruido raro", glossary))
    }

    @Test
    fun `clase del literal`() {
        val cases = listOf(
            "El variador muestra el fallo F0009" to LiteralClass.CODE,
            "¿qué es la alarma A2001?" to LiteralClass.CODE,
            "Aparece F0007 en la pantalla del variador" to LiteralClass.CODE,
            "Me sale el error 0016" to LiteralClass.CODE,
            "¿Para qué sirve el parámetro 9905?" to LiteralClass.PARAMETER,
            "la entrada DI1 no responde" to null,
            "¿Qué es el 2001?" to null,
        )
        for ((question, expected) in cases) {
            assertEquals(question, expected, QueryTerms.literalClass(question))
        }
    }

    @Test
    fun `una fila de la tabla glosario del índice separa las listas por barras`() {
        assertEquals(
            GlossaryEntry(listOf("bus de continua", "circuito intermedio"), listOf("\"dc bus\"", "\"intermediate circuit\"")),
            GlossaryEntry.fromRow("bus de continua|circuito intermedio", "\"dc bus\"|\"intermediate circuit\""),
        )
    }
}
