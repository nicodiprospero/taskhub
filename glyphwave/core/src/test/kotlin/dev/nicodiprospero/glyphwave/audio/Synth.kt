package dev.nicodiprospero.glyphwave.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic test audio: sines, a 120 BPM kick (decaying 55 Hz sine), hi-hats (filtered noise bursts). */
object Synth {
    const val RATE = 48_000

    fun sine(freq: Float, amp: Float, startSample: Long, out: FloatArray, count: Int = out.size) {
        for (i in 0 until count) out[i] = amp * sin(2 * PI * freq * (startSample + i) / RATE).toFloat()
    }

    /**
     * Beat track: kick on every beat at [bpm], hat on every 8th note, optional sustained [toneHz] bass.
     * Fills [out] with samples starting at absolute sample [start].
     */
    fun track(start: Long, out: FloatArray, bpm: Float = 120f, kick: Boolean = true, hats: Boolean = true, toneHz: Float = 0f, seed: Int = 1) {
        val beatLen = (RATE * 60f / bpm).toLong()
        val rnd = Random(seed + start.toInt())
        for (i in out.indices) {
            val n = start + i
            var s = 0f
            if (kick) {
                val sinceKick = (n % beatLen).toFloat() / RATE
                s += 0.8f * exp(-sinceKick * 12f) * sin(2 * PI * 55.0 * sinceKick).toFloat()
            }
            if (hats) {
                val sinceHat = ((n % (beatLen / 2)).toFloat()) / RATE
                s += 0.25f * exp(-sinceHat * 60f) * (rnd.nextFloat() * 2f - 1f) * sin(2 * PI * 9_000.0 * n / RATE).toFloat()
            }
            if (toneHz > 0f) s += 0.2f * sin(2 * PI * toneHz * n / RATE).toFloat()
            out[i] = s.coerceIn(-1f, 1f)
        }
    }
}
