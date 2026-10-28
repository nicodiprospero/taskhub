package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * 2 · Bars — mirrored (bass in the tall centre columns, highs toward both edges), clipped to the circle.
 *
 * v3 (owner: "do the radial idea to bars"): instead of one hard band per column,
 *  - a smoothed spectral profile (neighbour-blended band points, cosine-interpolated across columns) so
 *    neighbouring bars flow into each other;
 *  - anti-aliased bar tops: the top LED gets the fractional part of the height, so bars glide instead of
 *    stepping a whole LED;
 *  - a soft glow just above each bar and sub-pixel peak dots that fall smoothly between rows.
 */
class ClassicBarsMode(private val mask: CircleMask) : MatrixMode {
    override val id = "bars"
    override val name = "Bars"

    var focus = BandFocus.FULL
    private val n = mask.length
    private val half = n / 2
    private val view = BandView(POINTS)
    private val profile = FloatArray(POINTS)
    private val peakProfile = FloatArray(POINTS)

    private val top = IntArray(n) { mask.rowSpan(it)!!.first }
    private val bottom = IntArray(n) { mask.rowSpan(it)!!.last }

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        view.update(audio, focus, tMs)
        for (b in 0 until POINTS) {
            val l = view.values[if (b == 0) 1 else b - 1]
            val r = view.values[if (b == POINTS - 1) POINTS - 2 else b + 1]
            profile[b] = 0.2f * l + 0.6f * view.values[b] + 0.2f * r
            peakProfile[b] = view.peaks[b]
        }
        for (x in 0 until n) {
            // Column → position along the half-spectrum (0 = centre/bass … 1 = edge/highs).
            val p = abs(x - half).toFloat() / half * (POINTS - 1)
            val b0 = floor(p).toInt().coerceAtMost(POINTS - 2)
            val f = p - b0
            val w = (1f - cos(f * Math.PI.toFloat())) * 0.5f
            val value = profile[b0] * (1 - w) + profile[b0 + 1] * w
            val peak = maxOf(peakProfile[b0] * (1 - w) + peakProfile[b0 + 1] * w, value)

            val height = (bottom[x] - top[x] + 1).toFloat()
            val h = value * height
            val full = floor(h).toInt()
            val frac = h - full
            // Body: brighter toward the top of the bar.
            for (k in 0 until full) {
                canvas.set(x, bottom[x] - k, 0.30f + 0.55f * (k + 1f) / height)
            }
            // Anti-aliased top LED + a soft glow above it.
            val topLevel = 0.30f + 0.55f * (full + 1f) / height
            if (frac > 0.02f) canvas.max(x, bottom[x] - full, topLevel * frac)
            canvas.max(x, bottom[x] - full - 1, 0.12f * value)

            // Sub-pixel peak dot (spread over the two nearest rows).
            if (peak > value + 0.03f) {
                val py = bottom[x] - peak * (height - 1)
                val y0 = floor(py).toInt()
                val fy = py - y0
                canvas.max(x, y0, PEAK_LEVEL * (1 - fy))
                canvas.max(x, y0 + 1, PEAK_LEVEL * fy)
            }
        }
    }

    private companion object {
        const val POINTS = 9 // spectrum points per half; interpolated across 13 columns
        const val PEAK_LEVEL = 0.5f
    }
}
