package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.Polar
import kotlin.math.abs

/**
 * 1 · Radial spectrum: bands around the circle (mirrored left/right, bass at the bottom, highs at the
 * top), bars growing outward from a small core; falling peak dots as a ring segment; the core flashes on beats.
 */
class RadialSpectrumMode(private val polar: Polar) : MatrixMode {
    override val id = "radial"
    override val name = "Radial"

    var focus = BandFocus.FULL
    private val view = BandView(BANDS)
    private val coreRadius = 2.2f

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        view.update(audio, focus, tMs)
        val span = polar.maxRadius - coreRadius
        val flash = beatEnvelope(audio, tMs)
        val px = canvas.pixels
        for (i in px.indices) {
            val r = polar.radius[i]
            if (r < coreRadius) {
                canvas.pixels[i] = canvas.toValue(0.25f + 0.75f * flash)
                continue
            }
            // Bass at the bottom (turn 0.5) → highs at the top (turn 0), mirrored left/right.
            val t = polar.turn[i]
            val s = abs(t - 0.5f) * 2f
            val b = (s * BANDS).toInt().coerceIn(0, BANDS - 1)
            val extent = coreRadius + view.values[b] * span
            val peakR = coreRadius + view.peaks[b] * span
            px[i] = when {
                r <= extent -> canvas.toValue(0.35f + 0.65f * ((r - coreRadius) / span).coerceIn(0f, 1f))
                abs(r - peakR) < 0.55f && view.peaks[b] > 0.04f -> canvas.toValue(0.55f)
                else -> 0
            }
        }
    }

    private companion object {
        const val BANDS = 12 // per side (mirrored) — wide enough wedges to read on 25×25
    }
}
