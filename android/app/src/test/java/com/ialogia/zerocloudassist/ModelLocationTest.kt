package com.ialogia.zerocloudassist

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ModelLocationTest {

    @Test
    fun `el modelo vive en la subcarpeta models de la carpeta externa`() {
        val externalFiles = File("/storage/emulated/0/Android/data/com.ialogia.zerocloudassist/files")
        assertEquals(
            File(externalFiles, "models/m.gguf"),
            ModelLocation.modelPath(externalFiles, "m.gguf"),
        )
    }

    @Test
    fun `la pista de adb push apunta a la carpeta models de la app`() {
        assertEquals(
            "adb push models/m.gguf /sdcard/Android/data/com.ialogia.zerocloudassist/files/models/",
            ModelLocation.adbPushHint("com.ialogia.zerocloudassist", "m.gguf"),
        )
    }
}
