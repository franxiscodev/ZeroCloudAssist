package com.ialogia.zerocloudassist

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Estado y ciclo de vida del modelo, con vida de proceso: la carga, la generación y la
 * liberación sobreviven a que la actividad se destruya a mitad de una operación.
 *
 * Todas las operaciones del motor pasan por [mutex]: [InferenceEngine] lanza excepción si se le
 * llama en un estado inesperado (p. ej. liberar mientras genera), así que nunca se solapan.
 */
object Assistant {
    const val MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private const val MAX_TOKENS = 200

    // Protocolo fijo del plan 01, §5.
    private const val SYSTEM_PROMPT =
        "Eres un asistente técnico industrial. Responde siempre en español, de forma breve, " +
            "con pasos numerados cuando haya que hacer algo. Si hay riesgo eléctrico, avisa primero."

    private const val TAG = "ZCA"
    private const val METRICS_TAG = "ZCA_METRICS"

    var status by mutableStateOf("Iniciando…")
        private set
    var entries by mutableStateOf(emptyList<Entry>())
        private set
    var metrics by mutableStateOf("")
        private set
    var ready by mutableStateOf(false)
        private set
    var generating by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var job: Job? = null

    private lateinit var appContext: Context
    private lateinit var engine: InferenceEngine
    private var loadMs = 0L

    /** Llamar en `onStart`: carga el modelo si existe; si no, deja en [status] el `adb push`. */
    fun start(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            engine = AiChat.getInferenceEngine(appContext)
        }
        exclusive { load() }
    }

    /** Llamar en `onStop`: cancela lo que esté en curso y libera modelo y contexto. */
    fun stop() {
        ready = false
        exclusive(cancelRunning = true) { release() }
    }

    /** Devuelve `false` si la pregunta no se ha aceptado (modelo sin cargar, generando, vacía). */
    fun generate(prompt: String): Boolean {
        if (!ready || generating || prompt.isBlank()) return false
        generating = true
        metrics = ""
        entries = Conversation.ask(entries, prompt)
        exclusive {
            try {
                answer(prompt.trim())
            } catch (e: CancellationException) {
                entries = Conversation.interrupt(entries)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error al generar", e)
                entries = Conversation.interrupt(entries)
                status = "Error al generar: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                generating = false
            }
        }
        return true
    }

    private fun exclusive(cancelRunning: Boolean = false, block: suspend () -> Unit) {
        if (cancelRunning) job?.cancel()
        job = scope.launch { mutex.withLock { block() } }
    }

    private suspend fun load() {
        if (engine.state.value.isModelLoaded) return

        val model = ModelLocation.modelPath(appContext.getExternalFilesDir(null)!!, MODEL_FILE)
        model.parentFile?.mkdirs()
        if (!model.exists()) {
            status = "Falta el modelo. Cópialo desde la raíz del repo con:\n" +
                ModelLocation.adbPushHint(appContext.packageName, MODEL_FILE)
            return
        }

        // El motor carga la librería nativa en segundo plano al crearse.
        val state = engine.state.first {
            it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
        }
        if (state is InferenceEngine.State.Error) withContext(Dispatchers.IO) { engine.cleanUp() }

        status = "Cargando modelo…"
        try {
            val t0 = SystemClock.elapsedRealtime()
            engine.loadModel(model.path)
            loadMs = SystemClock.elapsedRealtime() - t0
            engine.setSystemPrompt(SYSTEM_PROMPT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar el modelo", e)
            status = "Error al cargar el modelo: ${e.message ?: e.javaClass.simpleName}"
            return
        }
        Log.i(TAG, "Modelo cargado en $loadMs ms")
        status = "Listo · %s".format(MODEL_FILE)
        ready = true
    }

    private suspend fun answer(prompt: String) {
        val sentAt = SystemClock.elapsedRealtime()
        var firstTokenAt = 0L
        var tokens = 0
        engine.sendUserPrompt(prompt, MAX_TOKENS).collect { piece ->
            if (tokens == 0) firstTokenAt = SystemClock.elapsedRealtime()
            tokens++
            entries = Conversation.append(entries, piece)
        }
        if (tokens == 0) return

        val endAt = SystemClock.elapsedRealtime()
        val memory = ActivityManager.MemoryInfo().also {
            appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        metrics = Metrics.format(
            loadMs = loadMs,
            ttftMs = Metrics.ttftMs(sentAt, firstTokenAt),
            tokensPerSecond = Metrics.tokensPerSecond(tokens, firstTokenAt, endAt),
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            availMemBytes = memory.availMem,
        )
        Log.i(METRICS_TAG, "$metrics · $tokens tokens")
    }

    private suspend fun release() {
        val state = engine.state.value
        if (state.isModelLoaded || state is InferenceEngine.State.Error) {
            withContext(Dispatchers.IO) { engine.cleanUp() }
            entries = Conversation.released(entries)
            Log.i(TAG, "Modelo liberado")
        }
        status = "Modelo liberado"
    }
}
