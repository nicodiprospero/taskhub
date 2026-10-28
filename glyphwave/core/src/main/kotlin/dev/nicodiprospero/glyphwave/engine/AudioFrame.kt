package dev.nicodiprospero.glyphwave.engine

/**
 * One analysed audio frame, as consumed by the modes. Mutable and reused (zero allocation per frame):
 * the analyser fills one instance, [AudioFrameExchange] copies it across threads.
 */
class AudioFrame(val bandCount: Int = BANDS, val waveformSize: Int = WAVEFORM) {
    /** Smoothed log-spaced bands 0..1 ([BAND_F_MIN]..[BAND_F_MAX] Hz). */
    val bands = FloatArray(bandCount)

    /** Falling peak dots 0..1. */
    val peaks = FloatArray(bandCount)

    /** Latest waveform, decimated, -1..1. */
    val waveform = FloatArray(waveformSize)

    var rms = 0f
    /** Auto-gained overall level 0..1. */
    var level = 0f
    /** Auto-gained, smoothed range energies 0..1: bass ≈ 30–150 Hz (kick / 808), mid 150–2 kHz, high 4–16 kHz. */
    var bass = 0f
    var mid = 0f
    var high = 0f
    var beat = false
    var beatStrength = 0f
    /** Monotonic time of the last beat (ns), for decaying flashes. */
    var lastBeatNanos = Long.MIN_VALUE / 2
    var timestampNanos = 0L
    var sequence = 0L
    /** False when the engine is stopped or the source is silent → modes fall back to idle. */
    var active = false

    fun copyFrom(o: AudioFrame) {
        o.bands.copyInto(bands)
        o.peaks.copyInto(peaks)
        o.waveform.copyInto(waveform)
        rms = o.rms; level = o.level; bass = o.bass; mid = o.mid; high = o.high
        beat = o.beat; beatStrength = o.beatStrength; lastBeatNanos = o.lastBeatNanos
        timestampNanos = o.timestampNanos; sequence = o.sequence; active = o.active
    }

    fun clear() {
        bands.fill(0f); peaks.fill(0f); waveform.fill(0f)
        rms = 0f; level = 0f; bass = 0f; mid = 0f; high = 0f; beat = false; beatStrength = 0f
        active = false
    }

    companion object {
        const val BANDS = 32
        const val WAVEFORM = 64
        const val BAND_F_MIN = 40f
        const val BAND_F_MAX = 16_000f
    }
}

/** Thread-safe latest-value exchange between the audio thread and render loops. Allocation-free. */
class AudioFrameExchange(bandCount: Int = AudioFrame.BANDS) {
    private val latest = AudioFrame(bandCount)
    private val lock = Any()

    fun publish(frame: AudioFrame) = synchronized(lock) { latest.copyFrom(frame) }

    fun readInto(dst: AudioFrame) = synchronized(lock) { dst.copyFrom(latest) }

    fun clear() = synchronized(lock) { latest.clear() }
}
