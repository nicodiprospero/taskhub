package dev.nicodiprospero.glyphwave.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cheap per-block statistics used to evaluate audio sources (Phase 2): RMS, peak, block rate,
 * how long the signal has been digitally silent, and an auto-gained 0..1 level for the matrix.
 * Allocation-free.
 */
class LevelMeter(
    /** Peak below this counts as "silent" (8-bit Visualizer zero = exactly 0; PCM noise floor ~1e-4). */
    private val silenceThreshold: Float = 1e-4f,
    /** Running-peak decay per second for auto-gain. */
    private val gainDecayPerSecond: Float = 0.5f,
) {
    var rms = 0f; private set
    var peak = 0f; private set

    /** Blocks per second (EMA). */
    var blockRate = 0f; private set

    /** Continuous silence so far, in ms (0 when the last block had signal). */
    val silentForMs: Long get() = silentNanos / 1_000_000
    private var silentNanos = 0L

    /** RMS normalised against a slowly decaying running peak: 0..1. */
    var level = 0f; private set

    var blocks = 0L; private set

    private var lastNanos = Long.MIN_VALUE
    private var runningPeak = 0.05f

    fun process(samples: FloatArray, count: Int, timestampNanos: Long) {
        var sum = 0.0
        var pk = 0f
        for (i in 0 until count) {
            val s = samples[i]
            sum += (s * s).toDouble()
            val a = abs(s)
            if (a > pk) pk = a
        }
        rms = if (count > 0) sqrt(sum / count).toFloat() else 0f
        peak = pk
        blocks++

        val dtNanos = if (lastNanos == Long.MIN_VALUE) 0L else (timestampNanos - lastNanos).coerceAtLeast(0)
        lastNanos = timestampNanos
        if (dtNanos > 0) {
            val inst = (1e9 / dtNanos).toFloat()
            blockRate = if (blockRate == 0f) inst else blockRate * 0.9f + inst * 0.1f
        }
        silentNanos = if (pk < silenceThreshold) silentNanos + dtNanos else 0L

        val decay = 1f - gainDecayPerSecond * (dtNanos / 1e9f).coerceIn(0f, 1f)
        runningPeak = max(max(runningPeak * decay, rms), 0.02f)
        level = (rms / runningPeak).coerceIn(0f, 1f)
    }

    fun reset() {
        rms = 0f; peak = 0f; blockRate = 0f; silentNanos = 0L; level = 0f; blocks = 0L
        lastNanos = Long.MIN_VALUE
        runningPeak = 0.05f
    }
}
