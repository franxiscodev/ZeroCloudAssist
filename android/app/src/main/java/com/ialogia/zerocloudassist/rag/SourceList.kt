package com.ialogia.zerocloudassist.rag

/** Una página del manual citada en la respuesta, con su texto para desplegarlo en pantalla. */
data class Source(val page: Int, val chapter: String, val text: String)

object SourceList {
    /** Una fuente por página, en el orden de los fragmentos; los de la misma página se juntan. */
    fun from(chunks: List<Chunk>): List<Source> =
        chunks.groupBy { it.page }.map { (page, samePage) ->
            Source(page, samePage.first().chapter, samePage.joinToString("\n\n") { it.text })
        }
}
