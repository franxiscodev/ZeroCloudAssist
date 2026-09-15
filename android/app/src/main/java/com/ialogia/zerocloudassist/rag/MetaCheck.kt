package com.ialogia.zerocloudassist.rag

data class ManualMeta(val manual: String, val version: String)

sealed interface MetaResult {
    data class Ok(val meta: ManualMeta) : MetaResult
    data class Incompatible(val reason: String) : MetaResult
}

/**
 * Comprueba que la tabla `meta` del índice es la que esta versión de la app sabe leer. Que el
 * fichero exista lo comprueba `Assistant` antes de abrirlo, con el `adb push` que lo copia.
 */
object MetaCheck {
    const val SCHEMA = "1"
    /** El modelo de vectores de la app: `Assistant` carga `$EMBEDDINGS.gguf`. */
    const val EMBEDDINGS = "multilingual-e5-small-q8_0"

    fun check(meta: Map<String, String>): MetaResult {
        val schema = meta["esquema"]
        if (schema != SCHEMA) {
            return MetaResult.Incompatible("El índice tiene el esquema $schema y la app lee el $SCHEMA")
        }
        val embeddings = meta["embeddings"]
        if (embeddings != EMBEDDINGS) {
            return MetaResult.Incompatible("El índice se vectorizó con $embeddings y la app usa $EMBEDDINGS")
        }
        val manual = meta["manual"]
        val version = meta["version"]
        if (manual == null || version == null) {
            return MetaResult.Incompatible("Al índice le falta el nombre o la versión del manual")
        }
        return MetaResult.Ok(ManualMeta(manual, version))
    }
}
