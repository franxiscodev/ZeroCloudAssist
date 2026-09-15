package com.ialogia.zerocloudassist.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Casos de `MarkdownLite` del plan 02, §5 (E3). */
class MarkdownLiteTest {

    @Test
    fun `negrita dentro de un párrafo`() {
        assertEquals(
            listOf(Block.Paragraph(listOf(Span("Causa", bold = true), Span(": sobretemperatura")))),
            MarkdownLite.parse("**Causa**: sobretemperatura"),
        )
    }

    @Test
    fun `lista numerada`() {
        assertEquals(
            listOf(Block.ListItem(number = 1, spans = listOf(Span("Corte la alimentación")))),
            MarkdownLite.parse("1. Corte la alimentación"),
        )
    }

    @Test
    fun `lista con guion o asterisco`() {
        val item = listOf(Block.ListItem(number = null, spans = listOf(Span("Revise el ventilador"))))
        assertEquals(item, MarkdownLite.parse("- Revise el ventilador"))
        assertEquals(item, MarkdownLite.parse("* Revise el ventilador"))
    }

    @Test
    fun `título`() {
        assertEquals(listOf(Block.Heading(3, listOf(Span("Comprobaciones")))), MarkdownLite.parse("### Comprobaciones"))
    }

    @Test
    fun `negrita sin cerrar se pinta literal`() {
        assertEquals(listOf(Block.Paragraph(listOf(Span("**sin cerrar")))), MarkdownLite.parse("**sin cerrar"))
    }

    @Test
    fun `texto vacío`() {
        assertEquals(emptyList<Block>(), MarkdownLite.parse(""))
    }

    @Test
    fun `una línea por bloque y sin las líneas en blanco`() {
        assertEquals(
            listOf(
                Block.Paragraph(listOf(Span("Revise:"))),
                Block.ListItem(number = 1, spans = listOf(Span("El ventilador"))),
                Block.ListItem(number = 2, spans = listOf(Span("La temperatura", bold = true))),
            ),
            MarkdownLite.parse("Revise:\n\n1. El ventilador\n2. **La temperatura**\n"),
        )
    }
}
