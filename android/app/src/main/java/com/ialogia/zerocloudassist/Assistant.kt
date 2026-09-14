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
import com.arm.aichat.Embedder
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.ialogia.zerocloudassist.rag.Chunk
import com.ialogia.zerocloudassist.rag.LiteralClass
import com.ialogia.zerocloudassist.rag.ManualMeta
import com.ialogia.zerocloudassist.rag.ManualSearch
import com.ialogia.zerocloudassist.rag.ManualStore
import com.ialogia.zerocloudassist.rag.MetaCheck
import com.ialogia.zerocloudassist.rag.MetaResult
import com.ialogia.zerocloudassist.rag.PromptBuilder
import com.ialogia.zerocloudassist.rag.QueryTerms
import com.ialogia.zerocloudassist.rag.SafetyRules
import com.ialogia.zerocloudassist.rag.SourceList
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Estado y ciclo de vida del asistente, con vida de proceso: la carga, la generación y la
 * liberación sobreviven a que la actividad se destruya a mitad de una operación.
 *
 * Todas las operaciones del motor pasan por [mutex]: [InferenceEngine] lanza excepción si se le
 * llama en un estado inesperado (p. ej. liberar mientras genera), así que nunca se solapan. El
 * mismo mutex serializa el índice del manual, cuya conexión SQLite no admite hilos a la vez.
 */
object Assistant {
    const val MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private const val E5_FILE = "multilingual-e5-small-q8_0.gguf"
    private const val MANUAL_FILE = "acs355.sqlite"
    private const val MAX_TOKENS = 400
    private const val TOP_K = 10  // candidatos de FTS5 y de vectores antes de fusionar (como en G3)

    private const val TAG = "ZCA"
    private const val METRICS_TAG = "ZCA_METRICS"
    private const val ANSWER_TAG = "ZCA_RESPUESTA"

    var status by mutableStateOf("Iniciando…")
        private set
    var entries by mutableStateOf(Conversation.initial())
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

    /** El índice abierto y lo que la búsqueda consulta en cada pregunta. Se queda abierto al salir. */
    private class Manual(val store: ManualStore, val meta: ManualMeta) {
        val texts = store.chunks.associate { it.id to it.text }
        val chapters = store.chunks.associate { it.id to it.chapter }
        val tokens = store.chunks.associate { it.id to it.tokens }
    }

    private var manual: Manual? = null

    /** Llamar en `onStart`: carga modelos e índice; si falta algo, deja en [status] el `adb push`. */
    fun start(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            engine = AiChat.getInferenceEngine(appContext)
        }
        exclusive { load() }
    }

    /** Llamar en `onStop`: cancela lo que esté en curso y libera los dos modelos. */
    fun stop() {
        ready = false
        exclusive(cancelRunning = true) { release() }
    }

    /** Devuelve `false` si la pregunta no se ha aceptado (sin cargar, generando, vacía). */
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
        if (engine.state.value.isModelLoaded && Embedder.isLoaded && manual != null) return

        val model = requireFile(ModelLocation.MODELS, MODEL_FILE) ?: return
        val e5 = requireFile(ModelLocation.MODELS, E5_FILE) ?: return
        if (!openManual()) return

        // El motor carga la librería nativa en segundo plano al crearse.
        val state = engine.state.first {
            it is InferenceEngine.State.Initialized || it.isModelLoaded || it is InferenceEngine.State.Error
        }
        if (state is InferenceEngine.State.Error) withContext(Dispatchers.IO) { engine.cleanUp() }

        status = "Cargando modelos…"
        try {
            if (!engine.state.value.isModelLoaded) {
                val t0 = SystemClock.elapsedRealtime()
                engine.loadModel(model.path)
                loadMs = SystemClock.elapsedRealtime() - t0
                engine.setSystemPrompt(PromptBuilder.SYSTEM_PROMPT)
                Log.i(TAG, "Modelo cargado en $loadMs ms")
            }
            // e5 después del chat: el motor ya ha cargado la librería y los backends de ggml.
            if (!Embedder.isLoaded) {
                val t0 = SystemClock.elapsedRealtime()
                if (!Embedder.load(e5.path)) throw IOException("no se pudo cargar $E5_FILE")
                Log.i(TAG, "e5 cargado en ${SystemClock.elapsedRealtime() - t0} ms")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar los modelos", e)
            status = "Error al cargar los modelos: ${e.message ?: e.javaClass.simpleName}"
            return
        }
        val meta = manual!!.meta
        status = "Listo · ${meta.manual} · índice ${meta.version}"
        ready = true
    }

    /** El fichero, o `null` con el `adb push` que falta en [status]. */
    private fun requireFile(subdir: String, name: String): java.io.File? {
        val file = ModelLocation.filePath(appContext.getExternalFilesDir(null)!!, subdir, name)
        file.parentFile?.mkdirs()  // para que `adb push` tenga dónde copiar
        if (file.exists()) return file
        status = "Falta $name. Cópialo desde la raíz del repo con:\n" +
            ModelLocation.adbPushHint(appContext.packageName, subdir, name)
        return null
    }

    /** Abre el índice y comprueba su `meta`; si no vale, lo explica en [status]. */
    private suspend fun openManual(): Boolean {
        if (manual != null) return true
        val file = ModelLocation.filePath(appContext.getExternalFilesDir(null)!!, ModelLocation.MANUALS, MANUAL_FILE)
        file.parentFile?.mkdirs()
        val hint = ModelLocation.adbPushHint(appContext.packageName, ModelLocation.MANUALS, MANUAL_FILE)
        if (!file.exists()) {
            status = "Falta el manual ($MANUAL_FILE). Cópialo desde la raíz del repo con:\n$hint"
            return false
        }
        return withContext(Dispatchers.IO) {
            val t0 = SystemClock.elapsedRealtime()
            val store = try {
                ManualStore(file)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo abrir el índice", e)
                status = "No se pudo abrir el índice: ${e.message}\nCópialo de nuevo con:\n$hint"
                return@withContext false
            }
            when (val result = MetaCheck.check(store.meta, hint)) {
                is MetaResult.Ok -> {
                    manual = Manual(store, result.meta).also { it.store.vectors; it.store.glossary }
                    Log.i(TAG, "Índice ${result.meta.version}: ${store.chunks.size} chunks en " +
                        "${SystemClock.elapsedRealtime() - t0} ms")
                    true
                }
                is MetaResult.Incompatible -> {
                    store.close()
                    status = "El índice no vale: ${result.reason}.\nCópialo de nuevo con:\n${result.hint}"
                    false
                }
                is MetaResult.Missing -> {  // no pasa: el fichero existe
                    store.close()
                    false
                }
            }
        }
    }

    /** Búsqueda híbrida decidida en G3 (plan 02, §5): la misma que mide `zca-indice evaluar`. */
    private suspend fun retrieve(question: String): List<Chunk> {
        val manual = manual!!
        val store = manual.store
        val query = Embedder.embed("query: $question")
        return withContext(Dispatchers.IO) {
            val fts = QueryTerms.ftsQuery(question, store.glossary)?.let(store::ftsIds).orEmpty()
            val preferred = when (QueryTerms.literalClass(question)) {
                LiteralClass.CODE -> store.meta["capitulo_codigos"]
                LiteralClass.PARAMETER -> store.meta["capitulo_parametros"]
                null -> null
            }
            val ftsTop = ManualSearch.prioritize(
                fts, manual.texts, manual.chapters, QueryTerms.literals(question), preferred,
            ).take(TOP_K)
            val vectorTop = ManualSearch.cosineTopK(query, store.vectors, store.dimension, TOP_K)
                .map { store.chunks[it].id }
            ManualSearch.select(ManualSearch.rrf(listOf(ftsTop, vectorTop)), manual.tokens)
                .map(store.byId::getValue)
        }
    }

    private suspend fun answer(question: String) {
        val sentAt = SystemClock.elapsedRealtime()
        val chunks = retrieve(question)
        val searchMs = SystemClock.elapsedRealtime() - sentAt
        val safety = SafetyRules.check(question, chunks)
        // Fuentes y aviso antes de procesar el prompt, para que haya dónde mirar mientras espera.
        entries = Conversation.attach(entries, SourceList.from(chunks), safety)
        Log.i(TAG, "Búsqueda en $searchMs ms: p. ${chunks.map { it.page }}, aviso ${safety != null}")

        var firstTokenAt = 0L
        var tokens = 0
        val answer = StringBuilder()
        // Cada pregunta parte del prompt de sistema: lleva sus propios fragmentos del manual.
        engine.resetConversation()
        engine.sendUserPrompt(PromptBuilder.userTurn(question, chunks), MAX_TOKENS).collect { piece ->
            if (tokens == 0) firstTokenAt = SystemClock.elapsedRealtime()
            tokens++
            answer.append(piece)
            entries = Conversation.append(entries, piece)
        }
        val truncated = engine.lastResponseTruncated
        if (truncated) entries = Conversation.truncate(entries)
        Log.i(ANSWER_TAG, JSONObject()
            .put("pregunta", question)
            .put("paginas", JSONArray(chunks.map { it.page }))
            .put("aviso", safety != null)
            .put("cortada", truncated)
            .put("respuesta", answer.toString())
            .toString())
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
            searchMs = searchMs,
            manualTokens = chunks.sumOf { it.tokens },
        )
        Log.i(METRICS_TAG, "$metrics · $tokens tokens")
    }

    private suspend fun release() {
        val state = engine.state.value
        if (state.isModelLoaded || state is InferenceEngine.State.Error) {
            try {
                withContext(Dispatchers.IO) {
                    Embedder.unload()
                    engine.cleanUp()
                }
            } catch (e: IllegalStateException) {
                // El motor rechaza liberar en estados intermedios; mejor no liberar que cerrar la app.
                Log.e(TAG, "No se pudo liberar el modelo en ${state.javaClass.simpleName}", e)
                status = "No se pudo liberar el modelo"
                return
            }
            entries = Conversation.released(entries)
            Log.i(TAG, "Modelos liberados")
        }
        status = "Modelos liberados"
    }
}
