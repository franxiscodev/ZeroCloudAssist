package com.ialogia.zerocloudassist

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ModelLocationTest {

    @Test
    fun `cada fichero vive en su subcarpeta de la carpeta externa`() {
        val externalFiles = File("/storage/emulated/0/Android/data/com.ialogia.zerocloudassist/files")
        assertEquals(
            File(externalFiles, "models/m.gguf"),
            ModelLocation.filePath(externalFiles, ModelLocation.MODELS, "m.gguf"),
        )
        assertEquals(
            File(externalFiles, "manuales/acs355.sqlite"),
            ModelLocation.filePath(externalFiles, ModelLocation.MANUALS, "acs355.sqlite"),
        )
    }

    @Test
    fun `la pista de adb push copia desde models del repo a la subcarpeta de la app`() {
        assertEquals(
            "adb push models/m.gguf /sdcard/Android/data/com.ialogia.zerocloudassist/files/models/",
            ModelLocation.adbPushHint("com.ialogia.zerocloudassist", ModelLocation.MODELS, "m.gguf"),
        )
        assertEquals(
            "adb push models/acs355.sqlite /sdcard/Android/data/com.ialogia.zerocloudassist/files/manuales/",
            ModelLocation.adbPushHint("com.ialogia.zerocloudassist", ModelLocation.MANUALS, "acs355.sqlite"),
        )
    }
}
