package dev.nicodiprospero.glyphwave.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FftTest {
    @Test
    fun `full-scale sine peaks in its bin at about 1`() {
        val fft = Fft(1024)
        val input = FloatArray(1024)
        val bin = 40
        val freq = bin * Synth.RATE / 1024f
        Synth.sine(freq, 1f, 0, input)
        val mags = FloatArray(fft.binCount)
        fft.magnitudes(input, mags)
        val peak = mags.indices.maxBy { mags[it] }
        assertEquals(bin, peak)
        assertEquals(1f, mags[bin], 0.05f)
        // Hann leakage stays local: 5 bins away is > 40 dB down.
        assertTrue(mags[bin + 5] < 0.01f)
    }

    @Test
    fun `silence gives zeros`() {
        val fft = Fft(256)
        val mags = FloatArray(fft.binCount)
        fft.magnitudes(FloatArray(256), mags)
        assertTrue(mags.all { it == 0f })
    }
}
