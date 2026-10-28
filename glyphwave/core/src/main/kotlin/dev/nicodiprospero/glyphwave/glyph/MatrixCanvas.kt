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

    /**
     * Anti-aliased point: spreads [level] over the 4 cells around the sub-pixel position (x, y) with
     * bilinear weights, keeping the brighter value per cell. Makes moving dots glide instead of jump.
     */
    fun splat(x: Float, y: Float, level: Float) {
        val x0 = kotlin.math.floor(x).toInt()
        val y0 = kotlin.math.floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        max(x0, y0, level * (1 - fx) * (1 - fy))
        max(x0 + 1, y0, level * fx * (1 - fy))
        max(x0, y0 + 1, level * (1 - fx) * fy)
        max(x0 + 1, y0 + 1, level * fx * fy)
    }

    /** Anti-aliased filled dot of [radius] cells centred at sub-pixel (cx, cy); keeps the brighter value. */
    fun dot(cx: Float, cy: Float, radius: Float, level: Float) {
        val x0 = kotlin.math.floor(cx - radius - 1).toInt()
        val x1 = kotlin.math.ceil(cx + radius + 1).toInt()
        val y0 = kotlin.math.floor(cy - radius - 1).toInt()
        val y1 = kotlin.math.ceil(cy + radius + 1).toInt()
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x - cx
            val dy = y - cy
            val cover = (radius + 0.5f - kotlin.math.sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f)
            if (cover > 0f) max(x, y, level * cover)
        }
    }

    fun copyTo(out: IntArray) = pixels.copyInto(out)

    fun toValue(level: Float): Int = (level.coerceIn(0f, 1f) * maxValue).roundToInt()
}
