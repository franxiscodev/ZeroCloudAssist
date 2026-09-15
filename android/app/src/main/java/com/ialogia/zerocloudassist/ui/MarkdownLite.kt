package com.ialogia.zerocloudassist.ui

sealed interface Block {
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>) : Block
    /** [number] es el de una lista numerada; `null`, una viñeta. */
    data class ListItem(val number: Int?, val spans: List<Span>) : Block
}

data class Span(val text: String, val bold: Boolean = false)

/**
 * El Markdown que escribe el modelo, reducido a lo que la pantalla pinta: títulos, listas y
 * negritas, una línea por bloque. Llega a trozos en streaming, así que lo que está a medias (una
 * negrita sin cerrar) se deja literal en vez de fallar.
 */
object MarkdownLite {
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val ORDERED = Regex("""^(\d{1,3})[.)]\s+(.*)$""")
    private val BULLET = Regex("""^[-*•]\s+(.*)$""")

    fun parse(text: String): List<Block> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map(::block)

    private fun block(line: String): Block {
        HEADING.matchEntire(line)?.let { return Block.Heading(it.groupValues[1].length, spans(it.groupValues[2])) }
        ORDERED.matchEntire(line)?.let { return Block.ListItem(number = it.groupValues[1].toInt(), spans = spans(it.groupValues[2])) }
        BULLET.matchEntire(line)?.let { return Block.ListItem(number = null, spans = spans(it.groupValues[1])) }
        return Block.Paragraph(spans(line))
    }

    /** `**negrita**` en pares; un `**` sin pareja se queda como texto. */
    private fun spans(line: String): List<Span> {
        val result = mutableListOf<Span>()
        var rest = line
        while (true) {
            val open = rest.indexOf("**")
            val close = if (open < 0) -1 else rest.indexOf("**", open + 2)
            if (close < 0) break
            if (open > 0) result += Span(rest.substring(0, open))
            if (close > open + 2) result += Span(rest.substring(open + 2, close), bold = true)
            rest = rest.substring(close + 2)
        }
        if (rest.isNotEmpty()) result += Span(rest)
        return result
    }
}
