package com.ialogia.zerocloudassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Hola mundo del plan 01: sin diseño a propósito (la estética va en el plan 02). */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { AssistantScreen() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Assistant.start(this)
    }

    override fun onStop() {
        Assistant.stop()
        super.onStop()
    }
}

@Composable
private fun AssistantScreen() {
    var prompt by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()

    // Mientras genera, la vista sigue al texto nuevo; al terminar se puede subir a leer.
    LaunchedEffect(Assistant.generating) {
        if (Assistant.generating) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Assistant.status, style = MaterialTheme.typography.bodySmall)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Assistant.entries.forEach { entry ->
                when (entry) {
                    is Entry.Turn -> TurnView(entry)
                    is Entry.Notice -> Text(
                        entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
        if (Assistant.metrics.isNotEmpty()) {
            Text(
                Assistant.metrics,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                label = { Text("Pregunta") },
                maxLines = 4,
            )
            Button(
                onClick = { if (Assistant.generate(prompt)) prompt = "" },
                enabled = Assistant.ready && !Assistant.generating && prompt.isNotBlank(),
            ) {
                Text("Generar")
            }
        }
    }
}

@Composable
private fun TurnView(turn: Entry.Turn) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(turn.question, fontWeight = FontWeight.Bold)
        if (turn.answer.isNotEmpty()) Text(turn.answer)
        if (turn.interrupted) {
            Text(
                "(respuesta interrumpida)",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
        }
        if (turn.truncated) {
            Text(
                "(respuesta cortada por el límite de longitud)",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
