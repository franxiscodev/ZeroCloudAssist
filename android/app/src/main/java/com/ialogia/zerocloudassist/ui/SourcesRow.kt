package com.ialogia.zerocloudassist.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ialogia.zerocloudassist.rag.Source

/** "Fuentes" y un chip por página; al tocar uno se despliega su fragmento del manual (en inglés). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcesRow(sources: List<Source>) {
    var open by remember(sources) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("FUENTES", style = ZcaType.label, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp))
            sources.forEachIndexed { i, source ->
                PageChip(source.page, selected = open == i, onClick = { open = if (open == i) null else i })
            }
        }
        open?.let { FragmentBox(sources[it]) }
    }
}

/** Chip de página en monoespaciada. Después del MVP será un enlace al PDF en esa página (plan §8). */
@Composable
fun PageChip(page: Int, color: Color = ZcaColors.amber, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        "p. $page" + if (selected) " ▾" else "",
        style = ZcaType.chip,
        color = color,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) color else color.copy(alpha = 0.45f), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** El texto del manual de una fuente, con su página y capítulo. Al abrirse, la vista baja hasta él. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FragmentBox(source: Source) {
    val shape = RoundedCornerShape(8.dp)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(source) { requester.bringIntoView() }
    Column(
        Modifier.bringIntoViewRequester(requester)
            .fillMaxWidth().clip(shape).background(ZcaColors.surface).border(1.dp, ZcaColors.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = ZcaColors.amber)) { append("p. ${source.page}") }
                append(" · ${source.chapter}")
            },
            style = ZcaType.manual,
        )
        Text(source.text, style = ZcaType.fragment)
    }
}
