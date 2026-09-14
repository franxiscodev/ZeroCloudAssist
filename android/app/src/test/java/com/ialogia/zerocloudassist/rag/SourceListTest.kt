package com.ialogia.zerocloudassist.rag

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceListTest {

    @Test
    fun `una fuente por página en el orden de los fragmentos`() {
        val chunks = listOf(
            Chunk(id = 1, page = 362, chapter = "Fault tracing", text = "0009 MOT OVERTEMP (1/2)", tokens = 10),
            Chunk(id = 2, page = 360, chapter = "Fault tracing", text = "0003 DEV OVERTEMP", tokens = 10),
            Chunk(id = 3, page = 362, chapter = "Fault tracing", text = "0009 MOT OVERTEMP (2/2)", tokens = 10),
        )
        assertEquals(
            listOf(
                Source(362, "Fault tracing", "0009 MOT OVERTEMP (1/2)\n\n0009 MOT OVERTEMP (2/2)"),
                Source(360, "Fault tracing", "0003 DEV OVERTEMP"),
            ),
            SourceList.from(chunks),
        )
    }

    @Test
    fun `sin fragmentos no hay fuentes`() {
        assertEquals(emptyList<Source>(), SourceList.from(emptyList()))
    }
}
