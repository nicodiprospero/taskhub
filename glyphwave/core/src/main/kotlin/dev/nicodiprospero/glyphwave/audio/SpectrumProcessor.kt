package dev.nicodiprospero.glyphwave.audio

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Magnitude spectrum → [bandCount] log-spaced bands between [fMin] and [fMax], in dB, auto-gained
 * against a slowly decaying running peak, smoothed (fast attack / slow release), with peak-hold
 * "falling dots". Allocation-free after [configure].
 */
class SpectrumProcessor(
    val bandCount: Int = 32,
    val fMin: Float = 40f,
    val fMax: Float = 16_000f,
    /** dB below the running peak that maps to 0. 8-bit Visualizer noise floor is ~48 dB down. */
    var dynamicRangeDb: Float = 42f,
    var peakDecayDbPerSecond: Float = 6f,
    var attackMs: Float = 15f,
    var releaseMs: Float = 180f,
    var peakHoldMs: Float = 300f,
    /** Peak-dot gravity in "full heights per second²". */
    var gravity: Float = 3f,
) {
    /** Normalised, unsmoothed band levels 0..1. */
    val raw = FloatArray(bandCount)

    /** Smoothed band levels 0..1 — what modes draw. */
    val bands = FloatArray(bandCount)

    /** Falling peak dots 0..1. */
    val peaks = FloatArray(bandCount)

    private val loBin = IntArray(bandCount)
    private val hiBin = IntArray(bandCount)
    private val centreBin = FloatArray(bandCount)
    private val peakHold = FloatArray(bandCount)
    private val peakVelocity = FloatArray(bandCount)
    private var configuredFor = -1L
    private var runningPeakDb = -30f

    /** Centre frequency of band [i] (log scale). */
    fun centreFrequency(i: Int): Float = fMin * (fMax / fMin).pow((i + 0.5f) / bandCount)

    fun configure(sampleRate: Int, fftSize: Int) {
        val key = sampleRate.toLong() * 100_000 + fftSize
        if (key == configuredFor) return
        configuredFor = key
        val binHz = sampleRate.toFloat() / fftSize
        val maxBin = fftSize / 2
        for (i in 0 until bandCount) {
            val lo = fMin * (fMax / fMin).pow(i.toFloat() / bandCount)
            val hi = fMin * (fMax / fMin).pow((i + 1f) / bandCount)
            loBin[i] = (lo / binHz).toInt().coerceIn(1, maxBin)
            hiBin[i] = (hi / binHz).toInt().coerceIn(1, maxBin)
            centreBin[i] = (centreFrequency(i) / binHz).coerceIn(1f, maxBin.toFloat())
        }
    }

    /** [mags] = output of [Fft.magnitudes]; [dt] seconds since the previous call. */
    fun process(mags: FloatArray, dt: Float) {
        var frameMaxDb = -120f
        for (i in 0 until bandCount) {
            val v = if (hiBin[i] > loBin[i]) {
                var m = 0f
                for (b in loBin[i] until hiBin[i]) if (mags[b] > m) m = mags[b]
                m
            } else {
                // Band narrower than one bin (low end): interpolate at the centre frequency.
                val c = centreBin[i]
                val b0 = floor(c).toInt().coerceAtMost(mags.size - 2)
                val f = c - b0
                mags[b0] * (1 - f) + mags[b0 + 1] * f
            }
            val db = 20f * log10(v + 1e-7f)
            raw[i] = db
            if (db > frameMaxDb) frameMaxDb = db
        }
        runningPeakDb = max(frameMaxDb, runningPeakDb - peakDecayDbPerSecond * dt).coerceAtLeast(-60f)
        val floorDb = runningPeakDb - dynamicRangeDb

        val attack = 1f - exp(-dt * 1000f / attackMs)
        val release = 1f - exp(-dt * 1000f / releaseMs)
        for (i in 0 until bandCount) {
            val n = ((raw[i] - floorDb) / dynamicRangeDb).coerceIn(0f, 1f)
            raw[i] = n
            val cur = bands[i]
            bands[i] = cur + (n - cur) * (if (n > cur) attack else release)

            if (bands[i] >= peaks[i]) {
                peaks[i] = bands[i]
                peakHold[i] = peakHoldMs / 1000f
                peakVelocity[i] = 0f
            } else if (peakHold[i] > 0f) {
                peakHold[i] -= dt
            } else {
                peakVelocity[i] += gravity * dt
                peaks[i] = (peaks[i] - peakVelocity[i] * dt).coerceAtLeast(bands[i])
            }
        }
    }

    fun reset() {
        bands.fill(0f); peaks.fill(0f); raw.fill(0f); peakHold.fill(0f); peakVelocity.fill(0f)
        runningPeakDb = -30f
    }

    companion object {
        /** Index of the band containing [hz] for a processor spanning [fMin]..[fMax] with [count] bands. */
        fun bandIndexOf(hz: Float, fMin: Float, fMax: Float, count: Int): Float =
            (ln(hz / fMin) / ln(fMax / fMin) * count)
    }
}
