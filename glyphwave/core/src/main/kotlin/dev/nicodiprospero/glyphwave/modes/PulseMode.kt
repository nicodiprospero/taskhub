package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.Polar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * 3 · Pulse — concentric rings: the bass sets the main ring, mids a dimmer inner ring, every beat launches a
 * shockwave outward and flashes the rim.
 *
 * v2 (owner: "do the radial idea to pulse"): everything anti-aliased (rings are coverage-based, not
 * threshold-based), the inside fades softly into the ring, and the main ring breathes with gentle lobes
 * from the spectrum (4-fold mirrored, like Radial) instead of being a perfect hard circle.
 */
class PulseMode(private val polar: Polar) : MatrixMode {
    override val id = "pulse"
    override val name = "Pulse"

    private val view = BandView(POINTS)
    private val waveRadius = FloatArray(MAX_WAVES)
    private val waveLevel = FloatArray(MAX_WAVES)
    private var lastBeatSeen = Long.MIN_VALUE
    private var lastTMs = -1L

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        val dt = if (lastTMs < 0) 0f else ((tMs - lastTMs) / 1000f).coerceIn(0f, 0.1f)
        lastTMs = tMs
        view.update(audio, BandFocus.FULL, tMs)
        var mean = 0f
        for (b in 0 until POINTS) mean += view.values[b]
        mean /= POINTS

        if (audio.lastBeatNanos != lastBeatSeen && audio.lastBeatNanos > 0) {
            lastBeatSeen = audio.lastBeatNanos
            var slot = 0
            for (w in 1 until MAX_WAVES) if (waveLevel[w] < waveLevel[slot]) slot = w
            waveRadius[slot] = 1.5f
            waveLevel[slot] = 0.5f + 0.5f * audio.beatStrength
        }
        for (w in 0 until MAX_WAVES) if (waveLevel[w] > 0f) {
            waveRadius[w] += WAVE_SPEED * dt
            waveLevel[w] -= dt * 1.5f
            if (waveRadius[w] > polar.maxRadius + 1) waveLevel[w] = 0f
        }

        val main = 2.0f + audio.bass * (polar.maxRadius - 4.5f)
        val inner = 0.9f + audio.mid * main * 0.55f
        val flash = beatEnvelope(audio, tMs)
        val rimR = polar.maxRadius - 0.6f
        val rotation = (tMs % ROTATION_MS) / ROTATION_MS.toFloat()
        val px = canvas.pixels
        for (i in px.indices) {
            val r = polar.radius[i]

            // Gentle lobes on the main ring (±1.2 cells), 4-fold mirrored and slowly rotating.
            var t = polar.turn[i] + rotation
            if (t >= 1f) t -= 1f
            val q = (t * 4f) % 2f
            val s = (if (q < 1f) q else 2f - q) * (POINTS - 1)
            val b0 = floor(s).toInt().coerceAtMost(POINTS - 2)
            val f = s - b0
            val w = (1f - cos(f * Math.PI.toFloat())) * 0.5f
            val lobe = (view.values[b0] * (1 - w) + view.values[b0 + 1] * w) - mean
            val ringR = main + LOBE_CELLS * lobe

            // Soft fill that fades into the main ring.
            var v = if (r < ringR) 0.06f + 0.16f * audio.bass * (r / ringR) else 0f
            v = maxOf(v, ring(r, ringR, 1.1f) * (0.55f + 0.45f * audio.bass))
            v = maxOf(v, ring(r, inner, 0.8f) * 0.40f)
            for (k in 0 until MAX_WAVES) {
                if (waveLevel[k] > 0f) v = maxOf(v, ring(r, waveRadius[k], 0.9f) * waveLevel[k] * 0.75f)
            }
            if (flash > 0.02f) v = maxOf(v, ring(r, rimR, 1.0f) * flash)
            px[i] = canvas.toValue(v)
        }
    }

    /** Anti-aliased ring: 1 on the radius, falling linearly to 0 at ±[width]. */
    private fun ring(r: Float, radius: Float, width: Float): Float = (1f - abs(r - radius) / width).coerceAtLeast(0f)

    override fun reset() {
        waveLevel.fill(0f); lastTMs = -1L
    }

    private companion object {
        const val POINTS = 6
        const val LOBE_CELLS = 2.4f
        const val MAX_WAVES = 4
        const val WAVE_SPEED = 20f
        const val ROTATION_MS = 30_000L
    }
}
