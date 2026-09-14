package com.ialogia.zerocloudassist

import com.ialogia.zerocloudassist.Entry.Notice
import com.ialogia.zerocloudassist.Entry.Turn
import com.ialogia.zerocloudassist.rag.SafetyNotice
import com.ialogia.zerocloudassist.rag.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTest {

    @Test
    fun `la conversación empieza con el aviso de preguntas independientes`() {
        assertEquals(listOf(Notice(Conversation.INDEPENDENT_NOTICE)), Conversation.initial())
    }

    @Test
    fun `fuentes y aviso de seguridad van en el último turno`() {
        val sources = listOf(Source(362, "Fault tracing", "0009 MOT OVERTEMP"))
        val safety = SafetyNotice("Antes de intervenir…", 18)
        assertEquals(
            listOf(Turn("P1", "R1"), Turn("P2", sources = sources, safety = safety)),
            Conversation.attach(listOf(Turn("P1", "R1"), Turn("P2")), sources, safety),
        )
    }

    @Test
    fun `sin preguntas no se avisa de la liberación tras el aviso inicial`() {
        assertEquals(Conversation.initial(), Conversation.released(Conversation.initial()))
    }

    @Test
    fun `preguntar añade un turno con la pregunta y sin respuesta`() {
        assertEquals(listOf(Turn("¿Qué es un variador?")), Conversation.ask(emptyList(), "  ¿Qué es un variador?  "))
    }

    @Test
    fun `el texto generado se añade solo a la respuesta del último turno`() {
        val entries = listOf(Turn("P1", "R1"), Turn("P2", "Hola"))
        assertEquals(
            listOf(Turn("P1", "R1"), Turn("P2", "Hola mundo")),
            Conversation.append(entries, " mundo"),
        )
    }

    @Test
    fun `interrumpir marca el último turno`() {
        val entries = listOf(Turn("P1", "R1"), Turn("P2", "a medias"))
        assertEquals(
            listOf(Turn("P1", "R1"), Turn("P2", "a medias", interrupted = true)),
            Conversation.interrupt(entries),
        )
    }

    @Test
    fun `cortar por el límite de tokens marca el último turno`() {
        val entries = listOf(Turn("P1", "R1"), Turn("P2", "1. Paso\n6.**"))
        assertEquals(
            listOf(Turn("P1", "R1"), Turn("P2", "1. Paso\n6.**", truncated = true)),
            Conversation.truncate(entries),
        )
    }

    @Test
    fun `al liberar el modelo se avisa de que ya no recuerda lo anterior`() {
        val entries = listOf(Turn("P1", "R1"))
        assertEquals(
            listOf(Turn("P1", "R1"), Notice(Conversation.RELEASED_NOTICE)),
            Conversation.released(entries),
        )
    }

    @Test
    fun `sin conversación no hay aviso al liberar`() {
        assertEquals(emptyList<Entry>(), Conversation.released(emptyList()))
    }

    @Test
    fun `el aviso de liberación no se repite si no hubo preguntas nuevas`() {
        val entries = listOf(Turn("P1", "R1"), Notice(Conversation.RELEASED_NOTICE))
        assertEquals(entries, Conversation.released(entries))
    }
}
