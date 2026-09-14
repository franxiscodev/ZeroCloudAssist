package com.ialogia.zerocloudassist.rag

data class ManualMeta(val manual: String, val version: String)

sealed interface MetaResult {
    data class Ok(val meta: ManualMeta) : MetaResult
    data class Missing(val hint: String) : MetaResult
    data class Incompatible(val reason: String, val hint: String) : MetaResult
}

/** Comprueba que la tabla `meta` del índice es la que esta versión de la app sabe leer. */
object MetaCheck {
    const val SCHEMA = "1"
    const val EMBEDDINGS = "multilingual-e5-small-q8_0"

    /** [hint]: el `adb push` que copia un índice bueno. */
    fun check(meta: Map<String, String>?, hint: String): MetaResult {
        if (meta == null) return MetaResult.Missing(hint)
        val schema = meta["esquema"]
        if (schema != SCHEMA) {
            return MetaResult.Incompatible("El índice tiene el esquema $schema y la app lee el $SCHEMA", hint)
        }
        val embeddings = meta["embeddings"]
        if (embeddings != EMBEDDINGS) {
            return MetaResult.Incompatible("El índice se vectorizó con $embeddings y la app usa $EMBEDDINGS", hint)
        }
        val manual = meta["manual"]
        val version = meta["version"]
        if (manual == null || version == null) {
            return MetaResult.Incompatible("Al índice le falta el nombre o la versión del manual", hint)
        }
        return MetaResult.Ok(ManualMeta(manual, version))
    }
}
