package com.ialogia.zerocloudassist

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsTest {

    @Test
    fun `tok por segundo cuenta desde el primer token hasta el final`() {
        assertEquals(8.0, Metrics.tokensPerSecond(tokens = 20, firstTokenAtMs = 1_000, endAtMs = 3_500), 1e-9)
    }

    @Test
    fun `tok por segundo es cero sin tokens`() {
        assertEquals(0.0, Metrics.tokensPerSecond(tokens = 0, firstTokenAtMs = 1_000, endAtMs = 3_500), 1e-9)
    }

    @Test
    fun `tok por segundo es cero si no ha pasado tiempo`() {
        assertEquals(0.0, Metrics.tokensPerSecond(tokens = 1, firstTokenAtMs = 1_000, endAtMs = 1_000), 1e-9)
    }

    @Test
    fun `ttft es el tiempo entre enviar y el primer token`() {
        assertEquals(800L, Metrics.ttftMs(sentAtMs = 1_000, firstTokenAtMs = 1_800))
    }

    @Test
    fun `formato en español con segundos, tok por segundo y megas`() {
        val line = Metrics.format(
            loadMs = 6_000,
            ttftMs = 800,
            tokensPerSecond = 8.66,
            nativeHeapBytes = 120L * 1024 * 1024,
            availMemBytes = 2_000_000_000,
        )
        assertEquals("carga 6,0 s · TTFT 0,8 s · 8,7 tok/s · heap 120 MB · libre 1907 MB", line)
    }
}
