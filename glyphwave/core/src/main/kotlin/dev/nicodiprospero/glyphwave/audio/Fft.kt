package dev.nicodiprospero.glyphwave.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Iterative radix-2 FFT with a Hann window. All tables precomputed; [magnitudes] is allocation-free. */
class Fft(val size: Int) {

    init {
        require(size >= 16 && size and (size - 1) == 0) { "FFT size must be a power of two ≥ 16" }
    }

    val binCount = size / 2 + 1

    private val cosTable = FloatArray(size / 2) { cos(2 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2 * PI * it / size).toFloat() }
    private val bitReverse = IntArray(size).also { rev ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) rev[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2 * PI * it / (size - 1))).toFloat() }

    /** Amplitude normalisation: a full-scale sine reads ≈ 1.0 in its bin. */
    private val scale = 2f / window.sum()

    private val re = FloatArray(size)
    private val im = FloatArray(size)

    /** Windowed magnitude spectrum of [input] (length [size]) into [out] (length [binCount]). */
    fun magnitudes(input: FloatArray, out: FloatArray) {
        for (i in 0 until size) {
            val j = bitReverse[i]
            re[j] = input[i] * window[i]
            im[j] = 0f
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += len
            }
            len *= 2
        }
        for (k in 0 until binCount) out[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
    }
}
