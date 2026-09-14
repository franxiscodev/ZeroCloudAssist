package com.ialogia.zerocloudassist.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaCheckTest {

    private val hint = "adb push models/acs355.sqlite /sdcard/Android/data/com.ialogia.zerocloudassist/files/manuales/"

    private val good = mapOf(
        "esquema" to "1",
        "manual" to "ABB ACS355 User's manual",
        "version" to "2026-09-14.2",
        "embeddings" to "multilingual-e5-small-q8_0",
        "dimension" to "384",
    )

    @Test
    fun `sin índice pide copiarlo con adb push`() {
        assertEquals(MetaResult.Missing(hint), MetaCheck.check(null, hint))
    }

    @Test
    fun `un esquema distinto es incompatible`() {
        val result = MetaCheck.check(good + ("esquema" to "2"), hint)
        assertTrue(result is MetaResult.Incompatible)
        assertEquals(hint, (result as MetaResult.Incompatible).hint)
    }

    @Test
    fun `vectores de otro modelo son incompatibles`() {
        val result = MetaCheck.check(good + ("embeddings" to "multilingual-e5-base-q8_0"), hint)
        assertTrue(result is MetaResult.Incompatible)
    }

    @Test
    fun `sin nombre o versión del manual es incompatible`() {
        assertTrue(MetaCheck.check(good - "version", hint) is MetaResult.Incompatible)
        assertTrue(MetaCheck.check(good - "manual", hint) is MetaResult.Incompatible)
    }

    @Test
    fun `un índice correcto da el manual y su versión`() {
        assertEquals(
            MetaResult.Ok(ManualMeta("ABB ACS355 User's manual", "2026-09-14.2")),
            MetaCheck.check(good, hint),
        )
    }
}
