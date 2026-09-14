package com.ialogia.zerocloudassist.rag

/** Un trozo del manual, tal como está en la tabla `chunks` del índice. */
data class Chunk(val id: Long, val page: Int, val chapter: String, val text: String, val tokens: Int)
