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
 * en los dos y en la tabla del plan 02.
 *
 * Fronteras de palabra explícitas en vez de `\b`: Android compila las expresiones con ICU, que no
 * admite el flag `(?U)` de Java, y así los tests de la JVM ejercitan lo mismo que el móvil. Palabra
 * = letra, número o `_`, como `\w` en Python; los dígitos de los códigos, solo ASCII.
 */
object QueryTerms {
    private const val START = """(?<![\p{L}\p{N}_])"""
    private const val END = """(?![\p{L}\p{N}_])"""
    private val TERM =
        Regex("""$START[FfAa]?([0-9]{4})$END|$START([A-Z]{2,4}[0-9]{0,2}|[A-Z]{1,3}[0-9]{1,2})$END""")
    private val CODE = Regex("""$START[fa][0-9]{4}$END|$START(?:fallo|falla|alarma|error|averia|codigo)$END""")
    private val PARAMETER = Regex("""${START}parametro""")
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
        val pattern = if (form.endsWith("*")) START + Regex.escape(form.dropLast(1)) else START + Regex.escape(form) + END
        return Regex(pattern).containsMatchIn(text)
    }
}
