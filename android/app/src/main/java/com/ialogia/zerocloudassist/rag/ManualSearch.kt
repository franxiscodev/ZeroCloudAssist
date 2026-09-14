package com.ialogia.zerocloudassist.rag

/**
 * Búsqueda pura sobre el índice: vectores, prioridad de FTS5, fusión RRF y presupuesto de tokens.
 * Gemelo de `tools/zca_tools/evaluar.py`: si cambia una regla, cambia en los dos y en la tabla del
 * plan 02. Los empates conservan el orden de aparición.
 */
object ManualSearch {

    /** Índices de los `k` vectores más parecidos. Todos tienen norma 1: el coseno es el producto escalar. */
    fun cosineTopK(query: FloatArray, vectors: FloatArray, dim: Int, k: Int): List<Int> {
        val scores = FloatArray(vectors.size / dim) { i ->
            var dot = 0f
            for (j in 0 until dim) dot += query[j] * vectors[i * dim + j]
            dot
        }
        return scores.indices.sortedByDescending { scores[it] }.take(k)
    }

    /**
     * Reordena los resultados de FTS5: primero los chunks con una línea que empieza por un término
     * literal (la entrada que lo define, no una mención), y de esos, primero los del capítulo
     * preferido. Dentro de cada grupo se conserva el orden de bm25.
     */
    fun prioritize(
        ids: List<Long>, texts: Map<Long, String>, chapters: Map<Long, String>,
        terms: List<String>, preferredChapter: String?,
    ): List<Long> {
        if (terms.isEmpty()) return ids
        val entries = terms.map { Regex("(?m)^" + Regex.escape(it) + "(?:[ \\t]|$)") }

        fun group(id: Long): Int = when {
            entries.none { it.containsMatchIn(texts.getValue(id)) } -> 2
            preferredChapter != null && chapters.getValue(id) == preferredChapter -> 0
            else -> 1
        }

        return ids.sortedBy(::group)
    }

    fun rrf(rankings: List<List<Long>>, k: Int = 60): List<Long> {
        val points = LinkedHashMap<Long, Double>()
        for (ranking in rankings) {
            ranking.forEachIndexed { index, id -> points[id] = (points[id] ?: 0.0) + 1.0 / (k + index + 1) }
        }
        return points.keys.sortedByDescending { points.getValue(it) }
    }

    /** Los primeros chunks que caben en el presupuesto; el que no cabe se salta y se sigue. */
    fun select(
        ids: List<Long>, tokens: Map<Long, Int>,
        maxTokens: Int = 300, maxChunks: Int = 2, header: Int = 8,
    ): List<Long> {
        val chosen = mutableListOf<Long>()
        var used = 0
        for (id in ids) {
            if (chosen.size == maxChunks) break
            val cost = tokens.getValue(id) + header
            if (used + cost <= maxTokens) {
                chosen += id
                used += cost
            }
        }
        return chosen
    }
}
