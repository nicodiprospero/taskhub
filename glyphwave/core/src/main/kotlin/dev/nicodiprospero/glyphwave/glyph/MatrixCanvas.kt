package dev.nicodiprospero.glyphwave.glyph

import kotlin.math.roundToInt

/**
 * A reusable n×n frame buffer with row-major values in 0..[MatrixSpec.MAX_VALUE].
 *
 * All drawing calls clip silently and never allocate, so they are safe in the render loop.
 * Brightness arguments are normalised floats (0f..1f) so modes never deal with the raw range.
 */
class MatrixCanvas(val length: Int, val maxValue: Int = MatrixSpec.MAX_VALUE) {

    val pixels = IntArray(length * length)

    fun clear() = pixels.fill(0)

    fun set(x: Int, y: Int, level: Float) {
        if (x !in 0 until length || y !in 0 until length) return
        pixels[y * length + x] = toValue(level)
    }

    /** Keeps the brighter of the existing and new value (additive-looking overlays without blowout). */
    fun max(x: Int, y: Int, level: Float) {
        if (x !in 0 until length || y !in 0 until length) return
        val i = y * length + x
        val v = toValue(level)
        if (v > pixels[i]) pixels[i] = v
    }

    fun setRaw(x: Int, y: Int, value: Int) {
        if (x !in 0 until length || y !in 0 until length) return
        pixels[y * length + x] = value.coerceIn(0, maxValue)
    }

    fun fillRect(x0: Int, y0: Int, x1: Int, y1: Int, level: Float) {
        val v = toValue(level)
        for (y in maxOf(0, y0)..minOf(length - 1, y1)) {
            for (x in maxOf(0, x0)..minOf(length - 1, x1)) pixels[y * length + x] = v
        }
    }

    fun copyTo(out: IntArray) = pixels.copyInto(out)

    fun toValue(level: Float): Int = (level.coerceIn(0f, 1f) * maxValue).roundToInt()
}
