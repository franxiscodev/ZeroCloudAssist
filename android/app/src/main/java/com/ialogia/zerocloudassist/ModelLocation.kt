package com.ialogia.zerocloudassist

import java.io.File

/**
 * El GGUF va fuera del APK, en la carpeta externa de la app (`getExternalFilesDir(null)`), que
 * no necesita permisos y es escribible por `adb push`.
 */
object ModelLocation {

    private const val MODELS_DIR = "models"

    fun modelPath(externalFilesDir: File, name: String): File =
        File(File(externalFilesDir, MODELS_DIR), name)

    /** Comando para copiar el modelo desde la raíz del repo (donde está `models/`). */
    fun adbPushHint(applicationId: String, name: String): String =
        "adb push $MODELS_DIR/$name /sdcard/Android/data/$applicationId/files/$MODELS_DIR/"
}
