package com.ialogia.zerocloudassist.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaCheckTest {

    private val good = mapOf(
        "esquema" to "1",
        "manual" to "ABB ACS355 User's manual",
        "version" to "2026-09-14.2",
        "embeddings" to "multilingual-e5-small-q8_0",
        "dimension" to "384",
    )

    @Test
    fun `un esquema distinto es incompatible`() {
        assertTrue(MetaCheck.check(good + ("esquema" to "2")) is MetaResult.Incompatible)
    }

    @Test
    fun `vectores de otro modelo son incompatibles`() {
        assertTrue(MetaCheck.check(good + ("embeddings" to "multilingual-e5-base-q8_0")) is MetaResult.Incompatible)
    }

    @Test
    fun `sin nombre o versión del manual es incompatible`() {
        assertTrue(MetaCheck.check(good - "version") is MetaResult.Incompatible)
        assertTrue(MetaCheck.check(good - "manual") is MetaResult.Incompatible)
    }

    @Test
    fun `un índice correcto da el manual y su versión`() {
        assertEquals(
            MetaResult.Ok(ManualMeta("ABB ACS355 User's manual", "2026-09-14.2")),
            MetaCheck.check(good),
        )
    }
}
