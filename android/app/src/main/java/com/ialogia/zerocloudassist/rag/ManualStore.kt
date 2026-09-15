package com.ialogia.zerocloudassist.rag

import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * El índice del manual (`acs355.sqlite`, esquema v1 de `tools/zca_tools/construir.py`) abierto en
 * solo lectura con el SQLite empaquetado, que trae FTS5. Al abrir solo lee `meta`: los chunks, los
 * vectores y el glosario se cargan la primera vez que se piden, o todos a la vez con [preload],
 * después de comprobar `meta` con [MetaCheck]. Una conexión SQLite no admite hilos a la vez: `Assistant` serializa las llamadas.
 * Sin tests JVM (SQLite nativo): se verifica en el móvil.
 */
class ManualStore(path: File) : AutoCloseable {
    private val connection = BundledSQLiteDriver().open(path.path, SQLITE_OPEN_READONLY)

    val meta: Map<String, String> = rows("SELECT clave, valor FROM meta") { it.getText(0) to it.getText(1) }.toMap()

    /** En el orden de `id`; la posición `i` corresponde al vector `i` de [vectors]. */
    val chunks: List<Chunk> by lazy {
        rows("SELECT id, pagina, capitulo, texto, tokens FROM chunks ORDER BY id") {
            Chunk(it.getLong(0), it.getInt(1), it.getText(2), it.getText(3), it.getInt(4))
        }
    }
    val byId: Map<Long, Chunk> by lazy { chunks.associateBy { it.id } }
    val texts: Map<Long, String> by lazy { chunks.associate { it.id to it.text } }
    val chapters: Map<Long, String> by lazy { chunks.associate { it.id to it.chapter } }
    val tokens: Map<Long, Int> by lazy { chunks.associate { it.id to it.tokens } }

    val dimension: Int get() = meta.getValue("dimension").toInt()

    /** Todos los vectores en una lista plana: `chunks.size × dimension` float32 con norma 1. */
    val vectors: FloatArray by lazy {
        val dim = dimension
        val result = FloatArray(chunks.size * dim)
        var row = 0
        rows("SELECT vector FROM chunks ORDER BY id") { statement ->
            val blob = statement.getBlob(0)
            check(blob.size == dim * Float.SIZE_BYTES) { "Vector de ${blob.size} bytes, se esperaban ${dim * 4}" }
            ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(result, row * dim, dim)
            row++
        }
        result
    }

    val glossary: List<GlossaryEntry> by lazy {
        rows("SELECT es, en FROM glosario ORDER BY rowid") { GlossaryEntry.fromRow(it.getText(0), it.getText(1)) }
    }

    /** Ids de todos los chunks que casan con la consulta FTS5, del más relevante (bm25) al menos. */
    fun ftsIds(query: String): List<Long> =
        rows("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY rank", bind = { it.bindText(1, query) }) {
            it.getLong(0)
        }

    /** Carga ya todo lo que consulta cada pregunta, para que no lo pague la primera. Tras [MetaCheck]. */
    fun preload() {
        byId; texts; chapters; tokens; vectors; glossary
    }

    override fun close() = connection.close()

    private fun <T> rows(
        sql: String,
        bind: (SQLiteStatement) -> Unit = {},
        read: (SQLiteStatement) -> T,
    ): List<T> = connection.prepare(sql).use { statement ->
        bind(statement)
        buildList { while (statement.step()) add(read(statement)) }
    }
}
