package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.Polar

/**
 * 4 · Bass · Treble (owner request after Phase 2): lows and highs get separate zones so a hi-hat never
 * "moves" the kick visual.
 *  - Centre disc: bass (≈30–150 Hz, kick / 808) sets the radius; beats punch the brightness.
 *  - Thin middle ring: mids, dim.
 *  - Outer zone: highs (4–16 kHz) as sparkle — the share of lit LEDs follows the treble energy, the pattern
 *    reshuffles ~15×/s so hats read as shimmer.
 */
class BassTrebleMode(private val polar: Polar) : MatrixMode {
    override val id = "split"
    override val name = "Bass · Treble"

    private val hash = FloatArray(polar.length * polar.length) { i -> hash01(i, 0) }
    private var lastBucket = -1L

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        val bucket = tMs / 66
        if (bucket != lastBucket) {
            lastBucket = bucket
            for (i in hash.indices) hash[i] = hash01(i, bucket.toInt())
        }
        val discR = 1.2f + audio.bass * (INNER_LIMIT - 1.2f)
        val punch = beatEnvelope(audio, tMs)
        val px = canvas.pixels
        for (i in px.indices) {
            val r = polar.radius[i]
            val v = when {
                r <= discR -> 0.45f + 0.35f * audio.bass + 0.2f * punch
                r < INNER_LIMIT -> 0f
                r < OUTER_START -> 0.08f + 0.35f * audio.mid
                hash[i] < audio.high * 0.85f -> 0.35f + 0.65f * hash01(i, 977)
                else -> 0f
            }
            px[i] = canvas.toValue(v)
        }
    }

    private companion object {
        const val INNER_LIMIT = 7.5f
        const val OUTER_START = 9f

        /** Deterministic 0..1 hash, allocation-free. */
        fun hash01(i: Int, seed: Int): Float {
            var h = i * 374_761_393 + seed * 668_265_263
            h = (h xor (h ushr 13)) * 1_274_126_177
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 65_535f
        }
    }
}
