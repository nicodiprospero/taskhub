package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.Polar
import kotlin.math.abs

/**
 * 3 · Pulse: concentric rings. The bass sets the radius of the main ring, mids a dimmer inner ring,
 * every beat launches a ring that travels outward, and beats flash the outer rim.
 */
class PulseMode(private val polar: Polar, private val mask: CircleMask) : MatrixMode {
    override val id = "pulse"
    override val name = "Pulse"

    private val waveRadius = FloatArray(MAX_WAVES)
    private val waveLevel = FloatArray(MAX_WAVES)
    private var lastBeatSeen = Long.MIN_VALUE
    private var lastTMs = -1L
    private val n = polar.length

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        val dt = if (lastTMs < 0) 0f else ((tMs - lastTMs) / 1000f).coerceIn(0f, 0.1f)
        lastTMs = tMs
        if (audio.lastBeatNanos != lastBeatSeen && audio.lastBeatNanos > 0) {
            lastBeatSeen = audio.lastBeatNanos
            spawnWave(audio.beatStrength)
        }
        for (w in 0 until MAX_WAVES) if (waveLevel[w] > 0f) {
            waveRadius[w] += WAVE_SPEED * dt
            waveLevel[w] -= dt * 1.4f
            if (waveRadius[w] > polar.maxRadius + 1) waveLevel[w] = 0f
        }

        val main = 1.5f + audio.bass * (polar.maxRadius - 3f)
        val inner = 0.8f + audio.mid * main * 0.6f
        val flash = beatEnvelope(audio, tMs)
        val px = canvas.pixels
        for (i in px.indices) {
            val r = polar.radius[i]
            var v = 0f
            if (r <= main) v = 0.12f + 0.1f * audio.bass
            if (abs(r - main) < 0.75f) v = maxOf(v, 0.95f)
            if (abs(r - inner) < 0.6f) v = maxOf(v, 0.45f)
            for (w in 0 until MAX_WAVES) {
                if (waveLevel[w] > 0f && abs(r - waveRadius[w]) < 0.7f) v = maxOf(v, waveLevel[w] * 0.8f)
            }
            if (flash > 0.02f && mask.isEdge(i % n, i / n)) v = maxOf(v, flash)
            px[i] = canvas.toValue(v)
        }
    }

    private fun spawnWave(strength: Float) {
        var slot = 0
        for (w in 1 until MAX_WAVES) if (waveLevel[w] < waveLevel[slot]) slot = w
        waveRadius[slot] = 1f
        waveLevel[slot] = 0.5f + 0.5f * strength
    }

    override fun reset() {
        waveLevel.fill(0f); lastTMs = -1L
    }

    private companion object {
        const val MAX_WAVES = 4
        const val WAVE_SPEED = 22f // cells per second
    }
}
