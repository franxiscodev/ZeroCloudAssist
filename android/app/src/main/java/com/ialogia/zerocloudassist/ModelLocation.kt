package com.ialogia.zerocloudassist

import java.io.File

/**
 * Los GGUF y el índice del manual van fuera del APK, en la carpeta externa de la app
 * (`getExternalFilesDir(null)`), que no necesita permisos y es escribible por `adb push`.
 */
object ModelLocation {

    const val MODELS = "models"
    const val MANUALS = "manuales"

    fun filePath(externalFilesDir: File, subdir: String, name: String): File =
        File(File(externalFilesDir, subdir), name)

    /** Comando para copiar el fichero desde la raíz del repo: todos se generan en `models/`. */
    fun adbPushHint(applicationId: String, subdir: String, name: String): String =
        "adb push models/$name /sdcard/Android/data/$applicationId/files/$subdir/"
}
