package com.ialogia.zerocloudassist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.arm.aichat.Embedder
import org.json.JSONArray
import java.io.File

/**
 * Comprobaciones de la etapa 2 del plan 02 sobre las piezas nativas, con el resultado en el log
 * `ZCA`. Solo corren en builds de depuración; la etapa 3 las sustituye por `ManualStore`.
 */
object DebugChecks {
    private const val TAG = "ZCA"

    /** Índice del manual, copiado con `tools/cargar-movil.ps1`. */
    fun manualPath(externalFilesDir: File) = File(externalFilesDir, "manuales/acs355.sqlite")

    fun enabled(context: Context) =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Paso 2.2: el SQLite empaquetado abre el índice en solo lectura y tiene FTS5 con bm25. Sin
     * `LIMIT`: la p. 362 es la cuarta coincidencia por id y la tercera por relevancia (Incidencias).
     */
    fun manualFts(externalFilesDir: File) {
        val db = manualPath(externalFilesDir)
        if (!db.exists()) {
            Log.w(TAG, "2.2: falta ${db.path}")
            return
        }
        val sql = "SELECT c.id, c.pagina FROM chunks_fts f JOIN chunks c ON c.id = f.rowid " +
            "WHERE chunks_fts MATCH '\"0009\"' ORDER BY rank"
        try {
            BundledSQLiteDriver().open(db.path, SQLITE_OPEN_READONLY).use { connection ->
                connection.prepare(sql).use { statement ->
                    val rows = buildList {
                        while (statement.step()) add("id ${statement.getLong(0)} p. ${statement.getLong(1)}")
                    }
                    Log.i(TAG, "2.2 FTS5 \"0009\": $rows")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "2.2 FTS5 falla", e)
        }
    }

    /**
     * Paso 2.3: vectores de las 12 preguntas de la batería en el móvil frente a los del PC
     * (`models/bateria-vectores.json`, de `zca-indice vectores-bateria`). Pide coseno ≥ 0,99 y
     * < 0,3 s por pregunta.
     */
    suspend fun embeddings(externalFilesDir: File) {
        val file = File(externalFilesDir, "debug/bateria-vectores.json")
        if (!file.exists() || !Embedder.isLoaded) {
            Log.w(TAG, "2.3: falta ${file.path} o e5 sin cargar")
            return
        }
        val battery = JSONArray(file.readText())
        var minCosine = 1f
        var maxMs = 0L
        for (i in 0 until battery.length()) {
            val item = battery.getJSONObject(i)
            val pc = item.getJSONArray("vector")
            val t0 = SystemClock.elapsedRealtime()
            val phone = Embedder.embed("query: " + item.getString("pregunta"))
            val ms = SystemClock.elapsedRealtime() - t0
            // Los dos vectores tienen norma 1: el coseno es el producto escalar.
            val cosine = phone.indices.sumOf { phone[it] * pc.getDouble(it) }.toFloat()
            minCosine = minOf(minCosine, cosine)
            maxMs = maxOf(maxMs, ms)
            Log.i(TAG, "2.3 %s coseno %.4f %d ms".format(item.getString("id"), cosine, ms))
        }
        Log.i(TAG, "2.3 resumen: coseno mínimo %.4f, máximo %d ms".format(minCosine, maxMs))
    }
}
