package com.ialogia.zerocloudassist

import com.ialogia.zerocloudassist.Entry.Notice
import com.ialogia.zerocloudassist.Entry.Turn
import com.ialogia.zerocloudassist.rag.SafetyNotice
import com.ialogia.zerocloudassist.rag.Source

sealed interface Entry {
    data class Turn(
        val question: String,
        val answer: String = "",
        val interrupted: Boolean = false,
        val truncated: Boolean = false,
        val sources: List<Source> = emptyList(),
        val safety: SafetyNotice? = null,
    ) : Entry
    data class Notice(val text: String) : Entry
}

/** Hilo de la conversación en pantalla. Funciones puras: cada cambio devuelve una lista nueva. */
object Conversation {
    const val RELEASED_NOTICE =
        "Modelo liberado al salir de la app: el asistente ya no recuerda lo anterior."
    const val INDEPENDENT_NOTICE =
        "Cada pregunta se responde por separado con el manual: el asistente no recuerda las anteriores."

    /** La conversación empieza con el aviso de que las preguntas son independientes. */
    fun initial(): List<Entry> = listOf(Notice(INDEPENDENT_NOTICE))

    fun ask(entries: List<Entry>, question: String): List<Entry> = entries + Turn(question.trim())

    /** Fuentes y aviso de seguridad del último turno; se publican antes de que empiece la respuesta. */
    fun attach(entries: List<Entry>, sources: List<Source>, safety: SafetyNotice?): List<Entry> =
        updateLastTurn(entries) { it.copy(sources = sources, safety = safety) }

    fun append(entries: List<Entry>, piece: String): List<Entry> =
        updateLastTurn(entries) { it.copy(answer = it.answer + piece) }

    fun interrupt(entries: List<Entry>): List<Entry> =
        updateLastTurn(entries) { it.copy(interrupted = true) }

    fun truncate(entries: List<Entry>): List<Entry> =
        updateLastTurn(entries) { it.copy(truncated = true) }

    /** El texto sigue en pantalla, pero el modelo recargado empieza sin memoria de la conversación. */
    fun released(entries: List<Entry>): List<Entry> =
        if (entries.isEmpty() || entries.last() is Notice) entries else entries + Notice(RELEASED_NOTICE)

    private inline fun updateLastTurn(entries: List<Entry>, update: (Turn) -> Turn): List<Entry> {
        val last = entries.lastOrNull() as? Turn ?: return entries
        return entries.dropLast(1) + update(last)
    }
}
