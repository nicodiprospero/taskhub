package dev.nicodiprospero.glyphwave.audio

import kotlin.math.sqrt

/**
 * Onset detector with an adaptive threshold. Fed an onset strength per analysis frame (the analyser
 * uses half-wave-rectified bass spectral flux), it reports a beat when the value exceeds
 * `mean + k·std` of the last [windowMs] and the refractory period has passed. Allocation-free.
 *
 * Why flux and not "energy > 1.4 × average" (the original design): hip-hop 808s sustain, so the rolling
 * energy average stays high and kicks stop standing out. Flux only measures how much the bass *rose*
 * since the previous frame, which is exactly the attack of a kick or a new 808 note (DECISIONS.md D-028).
 */
class BeatDetector(
    /** Threshold = mean + [sensitivityK] × std. Lower = more beats. */
    var sensitivityK: Float = 1.5f,
    var windowMs: Long = 1_000,
    var refractoryMs: Long = 180,
    /** Ignore onsets smaller than this fraction of the strongest onset in the window. */
    var minRelative: Float = 0.15f,
    capacity: Int = 256,
) {
    private val values = FloatArray(capacity)
    private val times = LongArray(capacity)
    private var head = 0
    private var size = 0
    private var lastBeatNanos = Long.MIN_VALUE / 2

    /** 0..1 for the most recent beat. */
    var strength = 0f; private set

    /** Current adaptive threshold (diagnostics). */
    var threshold = 0f; private set

    /** [gate] = false records the value but never reports a beat (e.g. bass not dominant). */
    fun process(onset: Float, tNanos: Long, gate: Boolean = true): Boolean {
        val windowNanos = windowMs * 1_000_000
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        var windowMax = 0f
        for (k in 0 until size) {
            val idx = (head - 1 - k + values.size) % values.size
            if (tNanos - times[idx] > windowNanos) break
            val v = values[idx]
            sum += v
            sumSq += (v * v).toDouble()
            if (v > windowMax) windowMax = v
            count++
        }
        values[head] = onset
        times[head] = tNanos
        head = (head + 1) % values.size
        if (size < values.size) size++

        if (count < 6) return false
        val mean = (sum / count).toFloat()
        val std = sqrt((sumSq / count - mean.toDouble() * mean).coerceAtLeast(0.0)).toFloat()
        threshold = mean + sensitivityK * std
        val beat = gate &&
            onset > threshold &&
            onset > 1e-4f &&
            onset >= minRelative * maxOf(windowMax, onset) &&
            tNanos - lastBeatNanos >= refractoryMs * 1_000_000
        if (beat) {
            lastBeatNanos = tNanos
            strength = (0.35f + 0.65f * ((onset - threshold) / (2f * std + 1e-6f))).coerceIn(0.35f, 1f)
        }
        return beat
    }

    fun reset() {
        head = 0; size = 0; strength = 0f; threshold = 0f
        lastBeatNanos = Long.MIN_VALUE / 2
    }
}
