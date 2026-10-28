package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.Polar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4 · Bass · Treble — lows and highs in separate, structured zones so a hi-hat never moves the kick visual.
 *  - Centre: an anti-aliased disc whose radius follows the bass (kick / 808), punched brighter on beats.
 *  - Every beat launches a shockwave ring from the disc edge outward.
 *  - Rim: 12 dots orbiting at r≈10.2 (anti-aliased, grow with their treble slice). Each dot follows one slice of the 4–16 kHz range (mirrored), so hats
 *    light the ring in a pattern, and the orbit speeds up with treble energy.
 *
 * v2 after owner feedback ("treble is ugly"): v1's random per-LED sparkle read as noise, not music, and the
 * hard-edged disc looked pixelated (DECISIONS.md D-029).
 */
class BassTrebleMode(private val polar: Polar) : MatrixMode {
    override val id = "split"
    override val name = "Bass · Treble"

    private val highs = BandView(DOTS / 2)
    private val waveRadius = FloatArray(MAX_WAVES)
    private val waveLevel = FloatArray(MAX_WAVES)
    private var lastBeatSeen = Long.MIN_VALUE
    private var lastTMs = -1L
    private var orbit = 0f // turns

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        val dt = if (lastTMs < 0) 0f else ((tMs - lastTMs) / 1000f).coerceIn(0f, 0.1f)
        lastTMs = tMs
        highs.update(audio, BandFocus.HIGHS, tMs)

        val discR = 1.3f + audio.bass * (DISC_MAX - 1.3f)
        val punch = beatEnvelope(audio, tMs)

        if (audio.lastBeatNanos != lastBeatSeen && audio.lastBeatNanos > 0) {
            lastBeatSeen = audio.lastBeatNanos
            var slot = 0
            for (w in 1 until MAX_WAVES) if (waveLevel[w] < waveLevel[slot]) slot = w
            waveRadius[slot] = discR
            waveLevel[slot] = 0.55f + 0.45f * audio.beatStrength
        }
        for (w in 0 until MAX_WAVES) if (waveLevel[w] > 0f) {
            waveRadius[w] += WAVE_SPEED * dt
            waveLevel[w] -= dt * 2.2f
            if (waveRadius[w] > ORBIT_R - 0.8f) waveLevel[w] = 0f
        }

        // Disc + shockwaves, per cell, anti-aliased.
        val px = canvas.pixels
        for (i in px.indices) {
            val r = polar.radius[i]
            var v = 0f
            val cover = (discR + 0.5f - r).coerceIn(0f, 1f)
            if (cover > 0f) v = cover * (0.5f + 0.25f * audio.bass + 0.25f * punch) * (1f - 0.25f * (r / (discR + 0.5f)))
            for (w in 0 until MAX_WAVES) {
                if (waveLevel[w] <= 0f) continue
                val ring = 1f - abs(r - waveRadius[w]) / 0.9f
                if (ring > 0f) v = maxOf(v, ring * waveLevel[w] * 0.8f)
            }
            px[i] = canvas.toValue(v)
        }

        // Treble orbit: dots glide (sub-pixel splats), speed follows the highs.
        orbit = (orbit + dt * (ORBIT_BASE + ORBIT_GAIN * audio.high)) % 1f
        val c = polar.centre
        for (d in 0 until DOTS) {
            val band = if (d < DOTS / 2) d else DOTS - 1 - d // mirrored: lowest highs face each other
            val value = highs.values[band]
            val level = 0.35f + 0.65f * value
            val a = 2.0 * PI * (d.toFloat() / DOTS + orbit)
            canvas.dot(c + ORBIT_R * sin(a).toFloat(), c - ORBIT_R * cos(a).toFloat(), 0.6f + 0.4f * value, level)
        }
    }

    override fun reset() {
        waveLevel.fill(0f); lastTMs = -1L
    }

    private companion object {
        const val DOTS = 12
        const val ORBIT_R = 10.2f
        const val ORBIT_BASE = 0.03f // turns/s when quiet
        const val ORBIT_GAIN = 0.25f // extra turns/s at full treble
        const val DISC_MAX = 6.5f
        const val MAX_WAVES = 3
        const val WAVE_SPEED = 18f // cells/s
    }
}
