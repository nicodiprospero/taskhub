package dev.nicodiprospero.glyphwave.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import kotlin.math.pow

/**
 * On-screen twin of the Glyph Matrix: round dots, only physical LEDs drawn, values in raw SDK units.
 * Shows exactly the frame that is (or would be) sent to setMatrixFrame.
 */
@Composable
fun MatrixPreview(frame: IntArray, mask: CircleMask, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val n = mask.length
        val cell = size.minDimension / n
        val radius = cell * 0.40f
        for (y in 0 until n) for (x in 0 until n) {
            if (!mask.contains(x, y)) continue
            val v = frame[y * n + x].coerceIn(0, MatrixSpec.MAX_VALUE) / MatrixSpec.MAX_VALUE.toFloat()
            // Perceived LED brightness ≈ inverse gamma of the drive level.
            val shade = if (v <= 0f) 0.11f else (0.16f + 0.84f * v.toDouble().pow(1 / 2.2).toFloat())
            drawCircle(
                color = Color(shade, shade, shade),
                radius = radius,
                center = Offset((x + 0.5f) * cell, (y + 0.5f) * cell),
            )
        }
    }
}
