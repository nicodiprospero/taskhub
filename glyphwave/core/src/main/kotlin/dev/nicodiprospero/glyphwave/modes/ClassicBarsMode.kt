package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas

/** 2 · Classic bars: one bar per column, clipped to the circle, with falling peak dots. */
class ClassicBarsMode(private val mask: CircleMask) : MatrixMode {
    override val id = "bars"
    override val name = "Bars"

    var focus = BandFocus.FULL
    private val n = mask.length
    private val view = BandView(n)

    // Column spans of the round matrix: top..bottom row with LEDs (columns are symmetric to rows).
    private val top = IntArray(n) { mask.rowSpan(it)!!.first }
    private val bottom = IntArray(n) { mask.rowSpan(it)!!.last }

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        view.update(audio, focus, tMs)
        for (x in 0 until n) {
            val height = bottom[x] - top[x] + 1
            val lit = (view.values[x] * height + 0.5f).toInt()
            for (k in 0 until lit) {
                val y = bottom[x] - k
                canvas.set(x, y, 0.4f + 0.6f * (k + 1f) / height)
            }
            if (view.peaks[x] > 0.03f) {
                val py = bottom[x] - (view.peaks[x] * (height - 1)).toInt()
                if (py < bottom[x] - lit + 1) canvas.set(x, py, 0.8f)
            }
        }
    }
}
