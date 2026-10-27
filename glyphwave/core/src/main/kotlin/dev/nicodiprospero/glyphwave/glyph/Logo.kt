package dev.nicodiprospero.glyphwave.glyph

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The GlyphWave mark: two periods of a sine wave across the matrix. Toy preview and app icon. */
object Logo {
    fun render(canvas: MatrixCanvas, mask: CircleMask) {
        canvas.clear()
        val n = canvas.length
        val c = (n - 1) / 2f
        val amp = n * 0.22f
        val period = n / 2f
        // Sample finely along x so steep segments stay connected.
        var i = 0
        val steps = n * 8
        while (i <= steps) {
            val fx = i / 8f
            val fy = c - amp * sin(2 * PI.toFloat() * (fx - c) / period)
            canvas.set(fx.roundToInt(), fy.roundToInt(), 1f)
            i++
        }
        mask.apply(canvas.pixels)
    }
}
