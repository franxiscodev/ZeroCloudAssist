package com.ialogia.zerocloudassist.rag

/**
 * Prompt de sistema y turno del usuario del plan 02, §6. Si cambia el prompt de sistema, cambia
 * aquí y en el plan. Los fragmentos llevan el capítulo y no la página, para no invitar al modelo a
 * escribir números de página.
 */
object PromptBuilder {
    const val SYSTEM_PROMPT =
        "Eres un asistente técnico industrial para el variador ABB ACS355. Responde siempre en " +
            "español, de forma breve y con pasos numerados cuando haya que hacer algo. Usa solo los " +
            "fragmentos del manual que acompañan a la pregunta. Si no contienen la respuesta, di: " +
            "\"No aparece en el manual\". No escribas números de página. Si hay riesgo eléctrico, " +
            "avisa primero."

    fun userTurn(question: String, chunks: List<Chunk>): String {
        val fragments = if (chunks.isEmpty()) {
            " (sin fragmentos)\n"
        } else {
            "\n" + chunks.withIndex().joinToString("") { (i, chunk) -> "[${i + 1}] ${chunk.chapter}\n${chunk.text}\n" }
        }
        return "Fragmentos del manual (en inglés):$fragments\nPregunta: $question"
    }
}
