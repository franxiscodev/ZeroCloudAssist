package com.ialogia.zerocloudassist.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ialogia.zerocloudassist.rag.SafetyNotice
import com.ialogia.zerocloudassist.rag.Source

/**
 * Tarjeta de riesgo eléctrico encima de la respuesta: la pone la app, no el modelo. Franja de
 * señal de peligro, el aviso y la cita de su página, que despliega ese texto del manual.
 */
@Composable
fun SafetyCard(notice: SafetyNotice, source: Source?) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(ZcaColors.hazardGround)
            .border(1.dp, ZcaColors.hazard.copy(alpha = 0.35f), shape),
    ) {
        HazardStripe(Modifier.fillMaxWidth().height(8.dp))
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarningSign(Modifier.size(width = 18.dp, height = 16.dp))
                Text("RIESGO ELÉCTRICO", style = ZcaType.cardTitle, color = ZcaColors.hazard)
            }
            Text(notice.text, style = ZcaType.answer.copy(fontSize = 14.sp, lineHeight = 20.sp))
            PageChip(
                notice.page,
                color = ZcaColors.hazard,
                selected = open,
                onClick = source?.let { { open = !open } },
            )
            if (open && source != null) FragmentBox(source)
        }
    }
}

/** Franja rayada amarilla y negra, como las etiquetas de peligro de los armarios eléctricos. */
@Composable
private fun HazardStripe(modifier: Modifier) {
    Canvas(modifier.background(ZcaColors.hazardDark)) {
        val band = 9.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawPath(
                Path().apply {
                    moveTo(x, size.height)
                    lineTo(x + size.height, 0f)
                    lineTo(x + size.height + band, 0f)
                    lineTo(x + band, size.height)
                    close()
                },
                ZcaColors.hazard,
            )
            x += 2 * band
        }
    }
}

@Composable
private fun WarningSign(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        drawPath(
            Path().apply {
                moveTo(size.width / 2, stroke)
                lineTo(size.width - stroke, size.height - stroke)
                lineTo(stroke, size.height - stroke)
                close()
            },
            ZcaColors.hazard,
            style = Stroke(width = stroke, join = StrokeJoin.Round),
        )
        val x = size.width / 2
        drawLine(ZcaColors.hazard, Offset(x, size.height * 0.38f), Offset(x, size.height * 0.64f), 1.8.dp.toPx(), StrokeCap.Round)
        drawCircle(ZcaColors.hazard, radius = 1.dp.toPx(), center = Offset(x, size.height * 0.79f))
    }
}
