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
        "cable del motor", "brk", "dc bus", "voltage", "ventilador", "sustitu", "reemplaz",
    )
    private val CHUNK_TRIGGERS = listOf(
        "capacitors discharge", "input power is applied", "dc bus", "electricity warning",
        "instructions in chapter safety", "disconnect it from the ac power",
    )
    // En el chunk anterior, "dc bus" solo describe una medida en tablas de fallos y parámetros: en
    // E5 sacaba la tarjeta en F0007 (B04) por la fila de fallo que la precede.
    private val PREVIOUS_TRIGGERS = CHUNK_TRIGGERS - "dc bus"

    /**
     * [previous] da el chunk anterior de cada fragmento: los pasos de un procedimiento siguen a su
     * `WARNING!`, y en E5 (B09) entraron los pasos sin el aviso.
     */
    fun check(question: String, chunks: List<Chunk>, previous: (Chunk) -> Chunk? = { null }): SafetyNotice? {
        val q = QueryTerms.normalize(question)
        val risky = QUESTION_TRIGGERS.any { it in q } ||
            chunks.any { mentions(it, CHUNK_TRIGGERS) } ||
            chunks.mapNotNull(previous).any { mentions(it, PREVIOUS_TRIGGERS) }
        return if (risky) SafetyNotice(NOTICE, PAGE) else null
    }

    private fun mentions(chunk: Chunk, triggers: List<String>): Boolean =
        QueryTerms.normalize(chunk.text).let { text -> triggers.any { it in text } }
}
