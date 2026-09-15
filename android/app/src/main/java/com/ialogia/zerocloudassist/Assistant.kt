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
import com.ialogia.zerocloudassist.rag.Source
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
import java.io.File
import java.io.IOException

/** Un fichero que la app necesita fuera del APK, para la pantalla de "falta …". */
data class RequiredFile(val label: String, val path: String, val present: Boolean)

/** Por qué la app no puede preguntar todavía: qué falta o no vale y cómo copiarlo. */
data class Blocked(val title: String, val reason: String?, val files: List<RequiredFile>, val command: String, val pill: String)

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
    // El mismo nombre que exige MetaCheck: los vectores de la pregunta y los del índice, del mismo modelo.
    private const val E5_FILE = "${MetaCheck.EMBEDDINGS}.gguf"
    private const val MANUAL_FILE = "acs355.sqlite"
    private const val MAX_TOKENS = 400
    private const val TOP_K = 10  // candidatos de FTS5 y de vectores antes de fusionar (como en G3)

    private const val TAG = "ZCA"
    private const val METRICS_TAG = "ZCA_METRICS"
    private const val ANSWER_TAG = "ZCA_RESPUESTA"

    /** En qué punto va la pregunta en curso, para la espera en pantalla. */
    sealed interface Phase {
        data object Idle : Phase
        data object Searching : Phase
        /** Procesando el prompt; [startedAt] (`elapsedRealtime`) es cuando se pulsó "Preguntar". */
        data class Processing(val startedAt: Long) : Phase
        data object Generating : Phase
    }

    /** La cabecera: nombre y versión del índice cuando está listo; si no, qué está pasando. */
    var status by mutableStateOf("Iniciando…")
        private set
    var entries by mutableStateOf(Conversation.initial())
        private set
    var metrics by mutableStateOf("")
        private set
    var ready by mutableStateOf(false)
        private set
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    val generating: Boolean get() = phase != Phase.Idle
    /** Qué fichero falta o no vale; `null` si están todos. */
    var blocked by mutableStateOf<Blocked?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var job: Job? = null

    private lateinit var appContext: Context
    private lateinit var engine: InferenceEngine
    private var loadMs = 0L

    private class Needed(val label: String, val title: String, val pill: String, val subdir: String, val name: String)

    private val MANUAL = Needed("Índice del manual", "Falta el manual", "sin manual", ModelLocation.MANUALS, MANUAL_FILE)
    private val NEEDED = listOf(
        Needed("Modelo de chat", "Falta el modelo de chat", "sin modelo", ModelLocation.MODELS, MODEL_FILE),
        Needed("Modelo de búsqueda", "Falta el modelo de búsqueda", "sin modelo", ModelLocation.MODELS, E5_FILE),
        MANUAL,
    )

    /**
     * El índice abierto, con su `meta` ya comprobada. Se queda abierto al salir de la app; [stamp]
     * (fecha y tamaño del fichero) dice si al volver hay que abrir uno nuevo.
     */
    private class Manual(val store: ManualStore, val meta: ManualMeta, val stamp: Pair<Long, Long>)

    private var manual: Manual? = null

    /** Llamar en `onStart`: carga modelos e índice; si falta algo, lo deja en [blocked]. */
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
        phase = Phase.Searching
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
                phase = Phase.Idle
            }
        }
        return true
    }

    /** El texto del manual de una página (la de la tarjeta de seguridad), para desplegarlo. */
    fun pageSource(page: Int): Source? =
        manual?.store?.chunks?.filter { it.page == page }?.let { SourceList.from(it).firstOrNull() }

    private fun exclusive(cancelRunning: Boolean = false, block: suspend () -> Unit) {
        if (cancelRunning) job?.cancel()
        job = scope.launch { mutex.withLock { block() } }
    }

    private fun hint(n: Needed) = ModelLocation.adbPushHint(appContext.packageName, n.subdir, n.name)

    private fun stampOf(file: File) = file.lastModified() to file.length()

    private suspend fun load() {
        if (engine.state.value.isModelLoaded && Embedder.isLoaded && manual != null) return

        val dir = appContext.getExternalFilesDir(null)!!
        val files = NEEDED.map { ModelLocation.filePath(dir, it.subdir, it.name) }
        val required = withContext(Dispatchers.IO) {
            files.forEach { it.parentFile?.mkdirs() }  // para que `adb push` tenga dónde copiar
            NEEDED.zip(files) { n, f -> RequiredFile(n.label, "${n.subdir}/${n.name}", f.exists()) }
        }
        NEEDED.zip(required).firstOrNull { !it.second.present }?.let { (n, _) ->
            block(Blocked(n.title, null, required, hint(n), n.pill))
            return
        }
        // Ya están los tres: si algo falla después, lo dice la cabecera, no un "falta …" viejo.
        blocked = null
        val (model, e5, index) = files
        if (!openManual(index, required)) return

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
        status = "${meta.manual} · índice ${meta.version}"
        ready = true
    }

    private fun block(reason: Blocked) {
        blocked = reason
        status = reason.title
    }

    /** Abre el índice y comprueba su `meta`; si no vale, lo deja en [blocked]. */
    private suspend fun openManual(file: File, required: List<RequiredFile>): Boolean {
        fun incompatible(reason: String) = block(Blocked("El índice no vale", reason, required, hint(MANUAL), MANUAL.pill))

        return withContext(Dispatchers.IO) {
            val stamp = stampOf(file)
            manual?.let { open ->
                // Con la app en segundo plano se puede copiar otro índice encima: entonces se reabre.
                if (open.stamp == stamp) return@withContext true
                open.store.close()
                manual = null
            }
            val t0 = SystemClock.elapsedRealtime()
            val store = try {
                ManualStore(file)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo abrir el índice", e)
                incompatible("No se pudo abrir: ${e.message}")
                return@withContext false
            }
            when (val result = MetaCheck.check(store.meta)) {
                is MetaResult.Ok -> {
                    store.preload()
                    manual = Manual(store, result.meta, stamp)
                    Log.i(TAG, "Índice ${result.meta.version}: ${store.chunks.size} chunks en " +
                        "${SystemClock.elapsedRealtime() - t0} ms")
                    true
                }
                is MetaResult.Incompatible -> {
                    store.close()
                    incompatible("${result.reason}.")
                    false
                }
            }
        }
    }

    /** Búsqueda híbrida decidida en G3 (plan 02, §5): la misma que mide `zca-indice evaluar`. */
    private suspend fun retrieve(question: String): List<Chunk> {
        val store = manual!!.store
        val query = Embedder.embed("query: $question")
        return withContext(Dispatchers.IO) {
            val fts = QueryTerms.ftsQuery(question, store.glossary)?.let(store::ftsIds).orEmpty()
            val preferred = when (QueryTerms.literalClass(question)) {
                LiteralClass.CODE -> store.meta["capitulo_codigos"]
                LiteralClass.PARAMETER -> store.meta["capitulo_parametros"]
                null -> null
            }
            val ftsTop = ManualSearch.prioritize(
                fts, store.texts, store.chapters, QueryTerms.literals(question), preferred,
            ).take(TOP_K)
            val vectorTop = ManualSearch.cosineTopK(query, store.vectors, store.dimension, TOP_K)
                .map { store.chunks[it].id }
            ManualSearch.select(ManualSearch.rrf(listOf(ftsTop, vectorTop)), store.tokens)
                .map(store.byId::getValue)
        }
    }

    private suspend fun answer(question: String) {
        val sentAt = SystemClock.elapsedRealtime()
        val chunks = retrieve(question)
        val searchMs = SystemClock.elapsedRealtime() - sentAt
        val byId = manual!!.store.byId
        val safety = SafetyRules.check(question, chunks) { byId[it.id - 1] }
        // Fuentes y aviso antes de procesar el prompt, para que haya dónde mirar mientras espera.
        entries = Conversation.attach(entries, SourceList.from(chunks), safety)
        phase = Phase.Processing(sentAt)
        Log.i(TAG, "Búsqueda en $searchMs ms: p. ${chunks.map { it.page }}, aviso ${safety != null}")

        var firstTokenAt = 0L
        var tokens = 0
        val answer = StringBuilder()
        // Cada pregunta parte del prompt de sistema: lleva sus propios fragmentos del manual.
        engine.resetConversation()
        engine.sendUserPrompt(PromptBuilder.userTurn(question, chunks), MAX_TOKENS).collect { piece ->
            if (tokens == 0) {
                firstTokenAt = SystemClock.elapsedRealtime()
                phase = Phase.Generating
            }
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
            Log.i(TAG, "Modelos liberados")
        }
        status = "Modelos liberados"
    }
}
