package dev.nicodiprospero.glyphwave.glyph

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Perceived level (what modes draw, 0..MAX) → LED drive value: gamma correction + global brightness cap,
 * via a precomputed LUT. Immutable: build a new one when the settings change. [apply] is allocation-free.
 */
class OutputStage(val gamma: Float = 2.2f, val brightnessCap: Float = 1f, private val max: Int = MatrixSpec.MAX_VALUE) {

    private val lut = IntArray(max + 1) { v ->
        (((v.toFloat() / max).pow(gamma)) * brightnessCap.coerceIn(0f, 1f) * max).roundToInt()
    }

    fun apply(src: IntArray, dst: IntArray) {
        for (i in src.indices) dst[i] = lut[src[i].coerceIn(0, max)]
    }

    fun drive(level: Int): Int = lut[level.coerceIn(0, max)]
}
