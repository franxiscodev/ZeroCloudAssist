package com.ialogia.zerocloudassist.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ialogia.zerocloudassist.R

/** Colores de `docs/diseno-ui.md` (mockup aprobado el 2026-09-14). Si cambian, en los dos sitios. */
object ZcaColors {
    val ground = Color(0xFF14110D)
    val surface = Color(0xFF1E1A15)
    val raised = Color(0xFF29231C)
    val line = Color(0xFF3A3228)
    val text = Color(0xFFEEE6D8)
    val muted = Color(0xFFA3988A)
    val faint = Color(0xFF6F665A)
    val amber = Color(0xFFF0A43A)
    val amberInk = Color(0xFF1B1206)
    val amberSoft = Color(0x24F0A43A)
    val hazard = Color(0xFFE8B923)
    val hazardGround = Color(0xFF2B2210)
    val hazardDark = Color(0xFF15110A)
    val ok = Color(0xFF7FB77E)
    val missing = Color(0xFFE2725B)
}

/** Las tres familias OFL del mockup, empaquetadas en `res/font` (licencias en `assets/licencias`). */
object ZcaFonts {
    val display = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )
    val body = FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    )
}

/** Estilos con nombre de su papel en la pantalla, no del tamaño. */
object ZcaType {
    val appName = TextStyle(fontFamily = ZcaFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = 0.6.sp)
    val manual = TextStyle(fontFamily = ZcaFonts.mono, fontSize = 11.5.sp, color = ZcaColors.muted)
    val pill = TextStyle(fontFamily = ZcaFonts.mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    val question = TextStyle(fontFamily = ZcaFonts.body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp)
    val answer = TextStyle(fontFamily = ZcaFonts.body, fontSize = 15.sp, lineHeight = 22.sp, color = ZcaColors.text)
    val notice = TextStyle(fontFamily = ZcaFonts.body, fontSize = 13.sp, lineHeight = 18.sp, color = ZcaColors.muted)
    val cardTitle = TextStyle(fontFamily = ZcaFonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.75.sp)
    val label = TextStyle(fontFamily = ZcaFonts.mono, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = ZcaColors.faint)
    val chip = TextStyle(fontFamily = ZcaFonts.mono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val code = TextStyle(fontFamily = ZcaFonts.mono, fontSize = 13.sp)
    val fragment = TextStyle(fontFamily = ZcaFonts.mono, fontSize = 12.sp, lineHeight = 17.sp, color = ZcaColors.text.copy(alpha = 0.85f))
    val metrics = TextStyle(fontFamily = ZcaFonts.mono, fontSize = 10.5.sp, lineHeight = 14.sp, color = ZcaColors.faint)
    val button = TextStyle(fontFamily = ZcaFonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.9.sp)
    val missingTitle = TextStyle(fontFamily = ZcaFonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ZcaColors.missing)
}

/** Tema único, oscuro y cálido: la app no tiene tema claro (plan 02, §8). */
@Composable
fun ZcaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ZcaColors.amber,
            onPrimary = ZcaColors.amberInk,
            background = ZcaColors.ground,
            onBackground = ZcaColors.text,
            surface = ZcaColors.ground,
            onSurface = ZcaColors.text,
            surfaceVariant = ZcaColors.surface,
            onSurfaceVariant = ZcaColors.muted,
            outline = ZcaColors.line,
            error = ZcaColors.missing,
        ),
        typography = Typography(bodyLarge = ZcaType.answer, bodyMedium = ZcaType.answer, labelLarge = ZcaType.button),
        content = content,
    )
}
