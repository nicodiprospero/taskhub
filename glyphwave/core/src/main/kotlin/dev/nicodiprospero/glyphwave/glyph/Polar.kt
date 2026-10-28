package dev.nicodiprospero.glyphwave.glyph

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** Precomputed polar coordinates of every cell, relative to the matrix centre. */
class Polar(val length: Int) {
    val centre = (length - 1) / 2f

    /** Largest radius that still has LEDs (≈ length/2). */
    val maxRadius = length / 2f

    /** Distance from the centre in cells. */
    val radius = FloatArray(length * length)

    /** Angle in turns 0..1, 0 = straight up, clockwise (as seen from the back of the phone). */
    val turn = FloatArray(length * length)

    init {
        for (y in 0 until length) for (x in 0 until length) {
            val dx = x - centre
            val dy = y - centre
            val i = y * length + x
            radius[i] = sqrt(dx * dx + dy * dy)
            var t = (atan2(dx, -dy) / (2 * PI)).toFloat()
            if (t < 0f) t += 1f
            turn[i] = t
        }
    }
}
