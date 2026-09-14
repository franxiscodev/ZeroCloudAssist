package com.ialogia.zerocloudassist.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ialogia.zerocloudassist.Assistant
import com.ialogia.zerocloudassist.Blocked
import com.ialogia.zerocloudassist.Entry
import kotlinx.coroutines.delay

/** Pantalla de taller del mockup aprobado (`docs/diseno-ui.md`): cabecera, hilo sin burbujas y campo. */
@Composable
fun ChatScreen() {
    var prompt by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()

    // Mientras genera, la vista sigue al texto nuevo; al terminar baja una última vez para que se vea
    // la marca de respuesta cortada, que llega después del último token.
    LaunchedEffect(Assistant.generating) {
        if (Assistant.generating) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
        else scroll.animateScrollTo(scroll.maxValue)
    }

    Column(Modifier.fillMaxSize().background(ZcaColors.ground).safeDrawingPadding()) {
        Header()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val blocked = Assistant.blocked
            if (blocked != null) {
                MissingPanel(blocked)
            } else {
                val entries = Assistant.entries
                entries.forEachIndexed { i, entry ->
                    when (entry) {
                        is Entry.Notice -> NoticeLine(entry.text)
                        is Entry.Turn -> TurnView(entry, waiting = i == entries.lastIndex && Assistant.generating)
                    }
                }
                if (entries.none { it is Entry.Turn }) {
                    Text(
                        "Pregunta por un código (F0009), un parámetro (9905) o lo que ves en la máquina.",
                        style = ZcaType.notice.copy(color = ZcaColors.faint),
                    )
                }
                if (Assistant.metrics.isNotEmpty() && !Assistant.generating) {
                    Text(Assistant.metrics, style = ZcaType.metrics)
                }
            }
        }
        // Al preguntar se cierra el teclado: si no, tapa media pantalla con la tarjeta y las fuentes.
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        Composer(prompt, onChange = { prompt = it }, onAsk = {
            if (Assistant.generate(prompt)) {
                prompt = ""
                keyboard?.hide()
                focus.clearFocus()
            }
        })
    }
}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ZcaColors.amber)) { append("ZCA") }
                    append(" · ABB ACS355")
                },
                style = ZcaType.appName,
                color = ZcaColors.text,
                modifier = Modifier.weight(1f),
            )
            StatePill()
        }
        Text(Assistant.status, style = ZcaType.manual, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 3.dp, bottom = 12.dp))
        HorizontalDivider(color = ZcaColors.line)
    }
}

@Composable
private fun StatePill() {
    val blocked = Assistant.blocked
    val (label, color) = when {
        blocked != null -> blocked.pill to ZcaColors.missing
        Assistant.generating -> "pensando" to ZcaColors.amber
        !Assistant.ready -> "cargando" to ZcaColors.amber
        else -> "listo" to ZcaColors.ok
    }
    Row(
        Modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = ZcaType.pill, color = color)
    }
}

@Composable
private fun NoticeLine(text: String) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(ZcaColors.line))
        Spacer(Modifier.width(10.dp))
        Text(text, style = ZcaType.notice)
    }
}

@Composable
private fun TurnView(turn: Entry.Turn, waiting: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(turn.question, style = ZcaType.question, color = ZcaColors.text)
        turn.safety?.let { SafetyCard(it, Assistant.pageSource(it.page)) }
        if (turn.answer.isNotEmpty()) MarkdownText(turn.answer)
        if (turn.truncated) EndMark("respuesta cortada: llegó al límite de longitud")
        if (turn.interrupted) EndMark("respuesta interrumpida")
        if (turn.sources.isNotEmpty()) SourcesRow(turn.sources)
        if (waiting && turn.answer.isEmpty()) Working(Assistant.phase)
    }
}

@Composable
private fun EndMark(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(1.dp).drawBehind {
                drawLine(
                    ZcaColors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            },
        )
        Text("— $text —", style = ZcaType.code.copy(fontSize = 12.sp, color = ZcaColors.muted))
    }
}

/** "buscando…" y luego "procesando el manual… N s", desde que se pulsó hasta la primera palabra. */
@Composable
private fun Working(phase: Assistant.Phase) {
    val startedAt = (phase as? Assistant.Phase.Processing)?.startedAt
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            delay(250)
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ZcaColors.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (startedAt == null) "buscando en el manual…" else "procesando el manual…",
                style = ZcaType.code,
                color = ZcaColors.text,
                modifier = Modifier.weight(1f),
            )
            if (startedAt != null) {
                Text("$seconds s", style = ZcaType.code.copy(fontWeight = FontWeight.SemiBold), color = ZcaColors.amber)
            }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = ZcaColors.amber, trackColor = ZcaColors.raised)
        Text("Con este móvil, la primera palabra llega a los 4–8 s.", style = ZcaType.notice.copy(fontSize = 12.sp))
    }
}

/** Estado "falta …": qué fichero falta o no vale y el comando exacto para copiarlo. */
@Composable
private fun MissingPanel(blocked: Blocked) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(blocked.title, style = ZcaType.missingTitle)
        blocked.reason?.let { Text(it, style = ZcaType.notice.copy(fontSize = 14.sp, lineHeight = 20.sp)) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (file in blocked.files) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (file.present) "✓" else "✗",
                        style = ZcaType.answer,
                        color = if (file.present) ZcaColors.ok else ZcaColors.missing,
                        modifier = Modifier.width(18.dp),
                    )
                    Column {
                        Text(file.label, style = ZcaType.answer.copy(fontSize = 14.sp))
                        Text(file.path, style = ZcaType.metrics.copy(fontSize = 11.sp))
                    }
                }
            }
        }
        Text("Cópialo desde la raíz del repo, con el móvil conectado:", style = ZcaType.notice.copy(fontSize = 14.sp))
        SelectionContainer {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ZcaColors.amber)) { append("$ ") }
                    append(blocked.command)
                },
                style = ZcaType.code.copy(fontSize = 12.sp, lineHeight = 18.sp, color = ZcaColors.text),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ZcaColors.surface)
                    .border(1.dp, ZcaColors.line, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Text(
            buildAnnotatedString {
                append("O todo lo que falte de una vez: ")
                withStyle(SpanStyle(fontFamily = ZcaFonts.mono, color = ZcaColors.amber)) { append("tools/cargar-movil.ps1") }
                append(". Después, vuelve a abrir la app.")
            },
            style = ZcaType.notice.copy(fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
}

@Composable
private fun Composer(prompt: String, onChange: (String) -> Unit, onAsk: () -> Unit) {
    Column {
        HorizontalDivider(color = ZcaColors.line)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                enabled = Assistant.blocked == null,
                placeholder = { Text("Pregunta sobre el variador…", style = ZcaType.answer.copy(color = ZcaColors.faint)) },
                textStyle = ZcaType.answer,
                shape = RoundedCornerShape(10.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZcaColors.amber,
                    unfocusedBorderColor = ZcaColors.line,
                    disabledBorderColor = ZcaColors.line,
                    focusedContainerColor = ZcaColors.surface,
                    unfocusedContainerColor = ZcaColors.surface,
                    disabledContainerColor = ZcaColors.surface,
                    cursorColor = ZcaColors.amber,
                    focusedTextColor = ZcaColors.text,
                    unfocusedTextColor = ZcaColors.text,
                ),
            )
            // "Preguntar" literal: los scripts de ADB (estres-movil, preguntar) buscan este texto.
            Button(
                onClick = onAsk,
                enabled = Assistant.ready && !Assistant.generating && prompt.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZcaColors.amber,
                    contentColor = ZcaColors.amberInk,
                    disabledContainerColor = ZcaColors.raised,
                    disabledContentColor = ZcaColors.faint,
                ),
            ) {
                Text("Preguntar", style = ZcaType.button)
            }
        }
    }
}
