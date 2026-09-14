package com.ialogia.zerocloudassist.rag

data class SafetyNotice(val text: String, val page: Int)

/**
 * Tarjeta de seguridad: la pone la app, no el modelo, cuando la pregunta o los fragmentos hablan de
 * riesgo eléctrico. Disparadores del plan 02 (§5, E3), comparados sin tildes ni mayúsculas.
 */
object SafetyRules {
    const val NOTICE =
        "Antes de intervenir: corte la alimentación, espere 5 minutos a que se descarguen los " +
            "condensadores y compruebe con un multímetro que no hay tensión."
    private const val PAGE = 18

    private val QUESTION_TRIGGERS = listOf(
        "tension", "bus de continua", "condensador", "desmont", "medir", "mido",
        "cable del motor", "brk", "dc bus", "voltage",
    )
    private val CHUNK_TRIGGERS = listOf(
        "capacitors discharge", "input power is applied", "dc bus", "electricity warning",
        "instructions in chapter safety", "disconnect it from the ac power",
    )

    fun check(question: String, chunks: List<Chunk>): SafetyNotice? {
        val q = QueryTerms.normalize(question)
        val risky = QUESTION_TRIGGERS.any { it in q } ||
            chunks.any { chunk -> QueryTerms.normalize(chunk.text).let { text -> CHUNK_TRIGGERS.any { it in text } } }
        return if (risky) SafetyNotice(NOTICE, PAGE) else null
    }
}
