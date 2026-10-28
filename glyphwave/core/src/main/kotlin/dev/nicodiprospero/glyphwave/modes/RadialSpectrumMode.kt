package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.Polar
import kotlin.math.abs
import kotlin.math.floor

/**
 * 1 · Radial — a "breathing flower". The spectrum is mapped around the circle with 4-fold mirror symmetry
 * (bass on the vertical axis, highs on the horizontal one) so the shape stays balanced whatever the genre,
 * interpolated smoothly between bands, slowly rotating. Drawn anti-aliased: a bright rim at the contour,
 * a faint fill inside, and a small core that flashes on beats.
 *
 * v2 after owner feedback ("radial is ugly"): v1 used hard 24-wedge sectors with bass at the bottom only,
 * which looked like a jagged, lopsided blob on 25×25 (DECISIONS.md D-029).
 */
class RadialSpectrumMode(private val polar: Polar) : MatrixMode {
    override val id = "radial"
    override val name = "Radial"

    var focus = BandFocus.FULL
    private val view = BandView(BANDS)
    private val profile = FloatArray(BANDS)
    private val coreRadius = 2.0f
    private val baseRadius = 4.0f
    private val maxExtent = polar.maxRadius - 0.6f

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        view.update(audio, focus, tMs)
        // Shape = a circle that breathes with the overall level + gentle lobes from the spectrum.
        // Bands are smoothed with their neighbours (mirror at the ends) so the contour has no spikes.
        var mean = 0f
        for (b in 0 until BANDS) mean += view.values[b]
        mean /= BANDS
        for (b in 0 until BANDS) {
            val l = view.values[if (b == 0) 1 else b - 1]
            val r = view.values[if (b == BANDS - 1) BANDS - 2 else b + 1]
            val smooth = 0.25f * l + 0.5f * view.values[b] + 0.25f * r
            profile[b] = (BREATH * mean + LOBES * (smooth - mean)).coerceIn(0f, 1f)
        }
        val flash = beatEnvelope(audio, tMs)
        val rotation = (tMs % ROTATION_MS) / ROTATION_MS.toFloat()
        val px = canvas.pixels
        for (i in px.indices) {
            val r = polar.radius[i]
            var t = polar.turn[i] + rotation
            if (t >= 1f) t -= 1f
            // 4-fold mirror: 0 on the vertical axis → 1 on the horizontal axis.
            val q = (t * 4f) % 2f
            val s = if (q < 1f) q else 2f - q
            val p = s * (BANDS - 1)
            val b0 = floor(p).toInt().coerceAtMost(BANDS - 2)
            val f = p - b0
            // Cosine interpolation between band points → rounded lobes.
            val w = (1f - kotlin.math.cos(f * Math.PI.toFloat())) * 0.5f
            val value = profile[b0] * (1 - w) + profile[b0 + 1] * w
            val extent = baseRadius + value * (maxExtent - baseRadius)

            var v = 0f
            // Faint fill inside the contour, slightly brighter outward.
            if (r < extent) v = 0.10f + 0.18f * (r / extent)
            // Bright anti-aliased rim on the contour.
            val rim = 1f - abs(r - extent) / RIM_WIDTH
            if (rim > 0f) v = maxOf(v, rim * (0.45f + 0.55f * value))
            // Core: anti-aliased disc, flashes on beats.
            val core = (coreRadius + 0.5f - r).coerceIn(0f, 1f)
            if (core > 0f) v = maxOf(v, core * (0.2f + 0.8f * flash))
            px[i] = canvas.toValue(v)
        }
    }

    private companion object {
        const val BANDS = 6 // per quarter; smoothed + interpolated, so the contour is round with soft lobes
        const val BREATH = 0.9f // how much the overall level grows the circle
        const val LOBES = 0.8f // how much band differences deform it
        const val RIM_WIDTH = 1.1f
        const val ROTATION_MS = 24_000L // one slow turn every 24 s
    }
}
