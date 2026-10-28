package dev.nicodiprospero.glyphwave.audio

/**
 * One block of captured audio, mono, normalised to -1..1.
 *
 * Instances are POOLED by each source (see [RawAudioPool]) to keep the capture path allocation-free:
 * a consumer must finish with a block before the source cycles back to it (pool depth ≥ 8 blocks,
 * i.e. > 100 ms of slack at 60 Hz).
 */
class RawAudio(capacity: Int) {
    val samples = FloatArray(capacity)
    var count = 0
    var sampleRate = 0

    /** Monotonic capture timestamp (elapsedRealtimeNanos on device). */
    var timestampNanos = 0L
}

/** Fixed ring of [RawAudio] blocks handed out round-robin. */
class RawAudioPool(size: Int, capacity: Int) {
    private val blocks = Array(size) { RawAudio(capacity) }
    private var next = 0

    fun obtain(): RawAudio {
        val b = blocks[next]
        next = (next + 1) % blocks.size
        return b
    }
}
