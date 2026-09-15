package com.arm.aichat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * ZeroCloudAssist: vectores de multilingual-e5-small por JNI (`embedder.cpp`), para buscar en el
 * manual. Vive en la misma librería nativa que el motor de chat pero con su propio modelo y
 * contexto, y sus llamadas van por un hilo propio, en serie.
 */
object Embedder {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    @Volatile
    var isLoaded = false
        private set

    private external fun nativeLoad(pathToModel: String): Boolean
    private external fun nativeEmbed(text: String): FloatArray?
    private external fun nativeUnload()

    /**
     * Requiere que el [InferenceEngine] esté en `Initialized`: es él quien carga la librería y
     * los backends de ggml. Devuelve `false` si no pudo cargar.
     */
    suspend fun load(pathToModel: String): Boolean = withContext(dispatcher) {
        if (!isLoaded) isLoaded = nativeLoad(pathToModel)
        isLoaded
    }

    /** Vector L2-normalizado de 384. El llamador antepone "query: ". */
    suspend fun embed(text: String): FloatArray = withContext(dispatcher) {
        check(isLoaded) { "Embedder sin cargar" }
        nativeEmbed(text) ?: throw RuntimeException("No se pudo vectorizar")
    }

    suspend fun unload() = withContext(dispatcher) {
        if (isLoaded) nativeUnload()
        isLoaded = false
    }
}
