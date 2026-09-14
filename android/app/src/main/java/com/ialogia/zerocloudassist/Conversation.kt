package com.ialogia.zerocloudassist

import com.ialogia.zerocloudassist.Entry.Notice
import com.ialogia.zerocloudassist.Entry.Turn

sealed interface Entry {
    data class Turn(
        val question: String,
        val answer: String = "",
        val interrupted: Boolean = false,
        val truncated: Boolean = false,
    ) : Entry
    data class Notice(val text: String) : Entry
}

/** Hilo de la conversación en pantalla. Funciones puras: cada cambio devuelve una lista nueva. */
object Conversation {
    const val RELEASED_NOTICE =
        "Modelo liberado al salir de la app: el asistente ya no recuerda lo anterior."

    fun ask(entries: List<Entry>, question: String): List<Entry> = entries + Turn(question.trim())

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
