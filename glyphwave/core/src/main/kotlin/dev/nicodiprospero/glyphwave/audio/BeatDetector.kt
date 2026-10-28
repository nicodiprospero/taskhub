package dev.nicodiprospero.glyphwave.audio

/**
 * Bass-energy onset detector: a beat is bass energy > [threshold] × its rolling average over
 * [windowMs], outside a [refractoryMs] window after the previous beat. Allocation-free.
 */
class BeatDetector(
    var threshold: Float = 1.4f,
    var windowMs: Long = 1_000,
    var refractoryMs: Long = 250,
    /** Ignore beats when the bass is essentially absent (relative to the window peak). */
    var minRelativeEnergy: Float = 0.08f,
    capacity: Int = 128,
) {
    private val energies = FloatArray(capacity)
    private val times = LongArray(capacity)
    private var head = 0
    private var size = 0
    private var lastBeatNanos = Long.MIN_VALUE / 2

    /** 0..1 for the most recent beat (decays in the renderer, not here). */
    var strength = 0f; private set

    /** [gate] = false records the energy but never reports a beat (e.g. bass not dominant). */
    fun process(energy: Float, tNanos: Long, gate: Boolean = true): Boolean {
        val windowNanos = windowMs * 1_000_000
        var sum = 0f
        var count = 0
        var windowMax = 0f
        for (k in 0 until size) {
            val idx = (head - 1 - k + energies.size) % energies.size
            if (tNanos - times[idx] > windowNanos) break
            sum += energies[idx]
            if (energies[idx] > windowMax) windowMax = energies[idx]
            count++
        }
        energies[head] = energy
        times[head] = tNanos
        head = (head + 1) % energies.size
        if (size < energies.size) size++

        if (count < 4) return false // need some history
        val avg = sum / count
        val ratio = if (avg > 1e-9f) energy / avg else 0f
        val loudEnough = energy >= minRelativeEnergy * maxOf(windowMax, energy)
        val beat = gate && ratio > threshold && loudEnough && tNanos - lastBeatNanos >= refractoryMs * 1_000_000
        if (beat) {
            lastBeatNanos = tNanos
            strength = ((ratio - threshold) / threshold).coerceIn(0f, 1f) * 0.7f + 0.3f
        }
        return beat
    }

    fun reset() {
        head = 0; size = 0; strength = 0f
        lastBeatNanos = Long.MIN_VALUE / 2
    }
}
