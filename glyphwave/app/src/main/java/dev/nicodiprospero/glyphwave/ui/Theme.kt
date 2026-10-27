package dev.nicodiprospero.glyphwave.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Nothing-style palette: pure black, white, greys, one red accent. */
object GwColors {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0E)
    val Line = Color(0xFF262626)
    val White = Color(0xFFFFFFFF)
    val Grey = Color(0xFF8A8A8A)
    val Red = Color(0xFFD71921)
    val Green = Color(0xFF3DDC84)
}

object GwType {
    /** Dot-matrix-like heading. A real 5x7 pixel font replaces it once PixelFont exists (Phase 5). */
    val Heading = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 4.sp,
        color = GwColors.White,
    )
    val Label = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        color = GwColors.Grey,
    )
    val Body = TextStyle(fontSize = 14.sp, color = GwColors.White, lineHeight = 20.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = GwColors.White, lineHeight = 15.sp)
}

@Composable
fun GlyphWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GwColors.Red,
            onPrimary = GwColors.White,
            secondary = GwColors.White,
            background = GwColors.Black,
            onBackground = GwColors.White,
            surface = GwColors.Surface,
            onSurface = GwColors.White,
            outline = GwColors.Line,
        ),
        content = content,
    )
}
