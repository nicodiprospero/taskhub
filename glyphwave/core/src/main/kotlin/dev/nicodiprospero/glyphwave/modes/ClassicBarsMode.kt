package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas

/**
 * 2 · Classic bars, mirrored: bass in the centre column (the tallest one on a round matrix), highs toward
 * both edges, one bar per column, falling peak dots. Mirroring keeps the picture balanced for bass-heavy
 * music; the original left→right layout put all the action in the short left columns (owner feedback).
 */
class ClassicBarsMode(private val mask: CircleMask) : MatrixMode {
    override val id = "bars"
    override val name = "Bars"

    var focus = BandFocus.FULL
    private val n = mask.length
    private val half = n / 2
    private val view = BandView(half + 1)

    // Column spans of the round matrix: top..bottom row with LEDs (columns are symmetric to rows).
    private val top = IntArray(n) { mask.rowSpan(it)!!.first }
    private val bottom = IntArray(n) { mask.rowSpan(it)!!.last }

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        view.update(audio, focus, tMs)
        for (x in 0 until n) {
            val b = kotlin.math.abs(x - half).coerceAtMost(half)
            val value = view.values[b]
            val peak = view.peaks[b]
            val height = bottom[x] - top[x] + 1
            val lit = (value * height + 0.5f).toInt()
            for (k in 0 until lit) {
                val y = bottom[x] - k
                canvas.set(x, y, 0.4f + 0.6f * (k + 1f) / height)
            }
            if (peak > 0.03f) {
                val py = bottom[x] - (peak * (height - 1)).toInt()
                if (py < bottom[x] - lit + 1) canvas.set(x, py, 0.8f)
            }
        }
    }
}
