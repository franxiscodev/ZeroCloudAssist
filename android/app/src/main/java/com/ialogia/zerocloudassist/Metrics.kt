package com.ialogia.zerocloudassist

import java.util.Locale

/** Cálculo y formato de las métricas que la app muestra y escribe en `ZCA_METRICS`. */
object Metrics {

    private val SPANISH = Locale.forLanguageTag("es-ES")
    private const val MB = 1024L * 1024L

    /** Velocidad de generación: tokens entre el primer token y el final, sin contar el prompt. */
    fun tokensPerSecond(tokens: Int, firstTokenAtMs: Long, endAtMs: Long): Double {
        val elapsedMs = endAtMs - firstTokenAtMs
        if (tokens <= 0 || elapsedMs <= 0) return 0.0
        return tokens * 1000.0 / elapsedMs
    }

    fun ttftMs(sentAtMs: Long, firstTokenAtMs: Long): Long = firstTokenAtMs - sentAtMs

    fun format(
        loadMs: Long,
        ttftMs: Long,
        tokensPerSecond: Double,
        nativeHeapBytes: Long,
        availMemBytes: Long,
        searchMs: Long,
        manualTokens: Int,
    ): String = String.format(
        SPANISH,
        "carga %.1f s · TTFT %.1f s · %.1f tok/s · heap %d MB · libre %d MB · búsqueda %.1f s · manual %d tok",
        loadMs / 1000.0,
        ttftMs / 1000.0,
        tokensPerSecond,
        nativeHeapBytes / MB,
        availMemBytes / MB,
        searchMs / 1000.0,
        manualTokens,
    )
}
