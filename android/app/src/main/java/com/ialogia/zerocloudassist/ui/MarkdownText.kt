package com.ialogia.zerocloudassist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** La respuesta del modelo pintada con los bloques de [MarkdownLite]: sin burbuja, a todo el ancho. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is Block.Heading -> Text(
                    block.spans.annotated(),
                    style = ZcaType.answer.copy(fontWeight = FontWeight.Bold, fontSize = (20 - block.level).coerceAtLeast(15).sp),
                )
                is Block.Paragraph -> Text(block.spans.annotated(), style = ZcaType.answer)
                is Block.ListItem -> Row {
                    Text(
                        if (block.ordered) "${block.number}." else "•",
                        style = ZcaType.code.copy(fontWeight = FontWeight.SemiBold, color = ZcaColors.amber, lineHeight = 22.sp),
                        modifier = Modifier.width(32.dp),  // "10." cabe en una línea (con 24 dp se partía)
                    )
                    Text(block.spans.annotated(), style = ZcaType.answer, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun List<Span>.annotated(): AnnotatedString = buildAnnotatedString {
    for (span in this@annotated) {
        if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
    }
}
