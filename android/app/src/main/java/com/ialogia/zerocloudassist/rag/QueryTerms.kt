package com.ialogia.zerocloudassist.rag

import java.text.Normalizer

/** Entrada del glosario taller → manual: formas en español y expansiones en sintaxis FTS5. */
data class GlossaryEntry(val es: List<String>, val en: List<String>) {
    companion object {
        /** La tabla `glosario` del índice guarda cada lista unida por "|" (`construir.py`). */
        fun fromRow(es: String, en: String) = GlossaryEntry(es.split("|"), en.split("|"))
    }
}

/** De qué habla la pregunta, para preferir el capítulo que define el código (`meta` del índice). */
enum class LiteralClass { CODE, PARAMETER }

/**
 * Términos de la pregunta para la búsqueda FTS5: literales (códigos, parámetros, siglas) y
 * expansiones del glosario. Gemelo de `tools/zca_tools/terminos.py`: si cambia una regla, cambia
 * en los dos y en la tabla del plan 02. Las expresiones llevan `(?U)` para que `\b` y `\d` sean
 * Unicode, como en Python.
 */
object QueryTerms {
    private val TERM = Regex("""(?U)\b[FfAa]?(\d{4})\b|\b([A-Z]{2,4}\d{0,2}|[A-Z]{1,3}\d{1,2})\b""")
    private val CODE = Regex("""(?U)\b[fa]\d{4}\b|\b(?:fallo|falla|alarma|error|averia|codigo)\b""")
    private val PARAMETER = Regex("""(?U)\bparametro""")
    private val MARKS = Regex("""\p{Mn}+""")

    /** Sin tildes y en minúsculas. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    /** `[FfAa]?\d{4}` → los 4 dígitos; siglas tal como aparecen; sin duplicados, en orden. */
    fun literals(question: String): List<String> =
        TERM.findAll(question).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.distinct().toList()

    /** Expansiones de las entradas del glosario que aparecen en la pregunta, sin duplicados. */
    fun expansions(question: String, glossary: List<GlossaryEntry>): List<String> {
        val text = normalize(question)
        val result = mutableListOf<String>()
        for (entry in glossary) {
            if (entry.es.any { matches(it, text) }) entry.en.filterTo(result) { it !in result }
        }
        return result
    }

    /** Literales entre comillas y expansiones del glosario, unidos por OR; `null` si no hay nada. */
    fun ftsQuery(question: String, glossary: List<GlossaryEntry> = emptyList()): String? {
        val parts = literals(question).map { "\"$it\"" } + expansions(question, glossary)
        return if (parts.isEmpty()) null else parts.joinToString(" OR ")
    }

    fun literalClass(question: String): LiteralClass? {
        val text = normalize(question)
        return when {
            CODE.containsMatchIn(text) -> LiteralClass.CODE
            PARAMETER.containsMatchIn(text) -> LiteralClass.PARAMETER
            else -> null
        }
    }

    /** Palabra entera, o prefijo si la forma acaba en `*`. */
    private fun matches(form: String, text: String): Boolean {
        val pattern = if (form.endsWith("*")) {
            """(?U)\b""" + Regex.escape(form.dropLast(1))
        } else {
            """(?U)\b""" + Regex.escape(form) + """\b"""
        }
        return Regex(pattern).containsMatchIn(text)
    }
}
