package dev.nicodiprospero.glyphwave.audio

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns raw blocks from any [AudioSource] into [AudioFrame]s: ring buffer → Hann FFT →
 * [SpectrumProcessor] bands → range energies → [BeatDetector] on the bass. Allocation-free.
 * One analysis per incoming block (Visualizer ≈ 20 Hz, playback capture 60 Hz).
 */
class AudioAnalyzer(val fftSize: Int = 1024, bandCount: Int = AudioFrame.BANDS) {

    val fft = Fft(fftSize)
    val spectrum = SpectrumProcessor(bandCount, AudioFrame.BAND_F_MIN, AudioFrame.BAND_F_MAX)
    val beats = BeatDetector()

    private val ring = FloatArray(fftSize)
    private var ringPos = 0
    private val linear = FloatArray(fftSize)
    private val mags = FloatArray(fft.binCount)
    /** Log magnitude of the low bins from the previous frame (bass flux). 16 bins cover 150 Hz at any rate ≤ 100 kHz. */
    private val prevBass = FloatArray(16) { ln(FLUX_FLOOR) }
    private var lastNanos = Long.MIN_VALUE
    private var sequence = 0L

    private val bassRange = RangeMeter()
    private val midRange = RangeMeter()
    private val highRange = RangeMeter()
    private val levelRange = RangeMeter()

    fun process(samples: FloatArray, count: Int, sampleRate: Int, tNanos: Long, out: AudioFrame) {
        // 1) append to ring, unroll the most recent fftSize samples
        for (i in 0 until count) {
            ring[ringPos] = samples[i]
            ringPos = (ringPos + 1) % fftSize
        }
        for (i in 0 until fftSize) linear[i] = ring[(ringPos + i) % fftSize]

        val dt = if (lastNanos == Long.MIN_VALUE) 0.05f else ((tNanos - lastNanos) / 1e9f).coerceIn(0.001f, 0.25f)
        lastNanos = tNanos

        // 2) spectrum
        fft.magnitudes(linear, mags)
        spectrum.configure(sampleRate, fftSize)
        spectrum.process(mags, dt)

        // 3) range energies (RMS of magnitudes in range)
        val binHz = sampleRate.toFloat() / fftSize
        val bassE = rangeEnergy(30f, 150f, binHz)
        val midE = rangeEnergy(150f, 2_000f, binHz)
        val highE = rangeEnergy(4_000f, 16_000f, binHz)

        // 4) beat = onset of the bass: half-wave-rectified log-magnitude flux over 30–150 Hz bins, gated
        //    on bass DOMINANCE so broadband hats/cymbals (which leak into the bass bins) can't fire it.
        val fullE = rangeEnergy(30f, 16_000f, binHz)
        val bassShare = if (fullE > 1e-9f) bassE / fullE else 0f
        val beat = beats.process(bassFlux(binHz), tNanos, gate = bassShare >= BASS_SHARE_FOR_BEAT)

        // 5) overall rms of the newest block
        var sum = 0.0
        for (i in 0 until count) sum += (samples[i] * samples[i]).toDouble()
        val rms = if (count > 0) sqrt(sum / count).toFloat() else 0f

        // 6) waveform: newest fftSize samples decimated to the frame's waveform size
        val ws = out.waveformSize
        val stride = fftSize / ws
        for (i in 0 until ws) out.waveform[i] = linear[i * stride]

        spectrum.bands.copyInto(out.bands)
        spectrum.peaks.copyInto(out.peaks)
        out.rms = rms
        out.level = levelRange.update(rms, dt)
        out.bass = bassRange.update(bassE, dt)
        out.mid = midRange.update(midE, dt)
        out.high = highRange.update(highE, dt)
        out.beat = beat
        if (beat) {
            out.beatStrength = beats.strength
            out.lastBeatNanos = tNanos
        }
        out.timestampNanos = tNanos
        out.sequence = ++sequence
        out.active = true
    }

    /** Σ max(0, ln m − ln m_prev) over the bass bins. Previous magnitudes kept per bin. */
    private fun bassFlux(binHz: Float): Float {
        val lo = max(1, (30f / binHz).toInt())
        val hi = (150f / binHz).toInt().coerceIn(lo, prevBass.size - 1)
        var flux = 0f
        for (b in lo..hi) {
            val cur = ln(mags[b] + FLUX_FLOOR)
            val d = cur - prevBass[b]
            if (d > 0f) flux += d
            prevBass[b] = cur
        }
        return flux
    }

    private fun rangeEnergy(fLo: Float, fHi: Float, binHz: Float): Float {
        val lo = max(1, (fLo / binHz).toInt())
        val hi = (fHi / binHz).toInt().coerceAtMost(mags.size - 1)
        if (hi < lo) return mags[lo]
        var s = 0f
        for (b in lo..hi) s += mags[b] * mags[b]
        return sqrt(s)
    }

    private companion object {
        /** Kick/808: bass ≫ rest. Noise-like hats: bass ≈ 5–10 % of the amplitude. */
        const val BASS_SHARE_FOR_BEAT = 0.35f

        /** Below this magnitude a bin counts as silent for the flux (8-bit Visualizer LSB ≈ 0.004). */
        const val FLUX_FLOOR = 0.003f
    }

    fun reset() {
        ring.fill(0f); ringPos = 0; lastNanos = Long.MIN_VALUE
        prevBass.fill(ln(FLUX_FLOOR))
        spectrum.reset(); beats.reset()
        bassRange.reset(); midRange.reset(); highRange.reset(); levelRange.reset()
    }

    /** Auto-gain + smoothing for one scalar: value / slowly decaying running peak, fast attack, slow release. */
    private class RangeMeter(
        private val decayPerSecond: Float = 0.35f,
        private val attackMs: Float = 10f,
        private val releaseMs: Float = 140f,
    ) {
        private var peak = 1e-4f
        private var value = 0f

        fun update(x: Float, dt: Float): Float {
            peak = max(max(peak * (1f - decayPerSecond * dt), x), 1e-4f)
            val n = (x / peak).coerceIn(0f, 1f)
            val k = 1f - exp(-dt * 1000f / (if (n > value) attackMs else releaseMs))
            value += (n - value) * k
            return value
        }

        fun reset() {
            peak = 1e-4f; value = 0f
        }
    }
}
