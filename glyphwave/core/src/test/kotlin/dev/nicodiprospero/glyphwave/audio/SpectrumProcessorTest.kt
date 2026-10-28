package dev.nicodiprospero.glyphwave.audio

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumProcessorTest {

    private fun settle(freq: Float, amp: Float = 0.5f): AudioAnalyzer {
        val a = AudioAnalyzer()
        val frame = AudioFrame()
        val block = FloatArray(800)
        var t = 0L
        repeat(60) { k ->
            Synth.sine(freq, amp, k * 800L, block)
            a.process(block, block.size, Synth.RATE, t, frame)
            t += 16_666_667
        }
        return a
    }

    @Test
    fun `sine sweep lights the matching band`() {
        val p = SpectrumProcessor()
        for (freq in listOf(60f, 120f, 440f, 1_000f, 3_000f, 8_000f, 12_000f)) {
            val a = settle(freq)
            val bands = a.spectrum.bands
            val loudest = bands.indices.maxBy { bands[it] }
            // Physical resolution: a 1024-point FFT at 48 kHz has 47 Hz bins, so allow ± one bin of frequency.
            val binHz = Synth.RATE / 1024f
            val lo = SpectrumProcessor.bandIndexOf(maxOf(p.fMin, freq - binHz), p.fMin, p.fMax, p.bandCount).toInt() - 1
            val hi = SpectrumProcessor.bandIndexOf(freq + binHz, p.fMin, p.fMax, p.bandCount).toInt() + 1
            assertTrue("$freq Hz → band $loudest, expected $lo..$hi", loudest in lo..hi)
            assertTrue("$freq Hz loudest band near full: ${bands[loudest]}", bands[loudest] > 0.8f)
        }
    }

    @Test
    fun `auto gain fills the range for quiet and loud input`() {
        for (amp in listOf(0.02f, 0.9f)) {
            val bands = settle(1_000f, amp).spectrum.bands
            assertTrue("amp $amp max ${bands.max()}", bands.max() > 0.8f)
        }
    }

    @Test
    fun `peaks hold then fall with gravity`() {
        val a = settle(1_000f)
        val frame = AudioFrame()
        val silence = FloatArray(800)
        val band = a.spectrum.bands.indices.maxBy { a.spectrum.bands[it] }
        val peakBefore = a.spectrum.peaks[band]
        var t = 60 * 16_666_667L
        // 200 ms: still holding
        repeat(12) { a.process(silence, 800, Synth.RATE, t, frame); t += 16_666_667 }
        assertEquals(peakBefore, a.spectrum.peaks[band], 0.01f)
        assertTrue(a.spectrum.bands[band] < peakBefore)
        // +1 s: fallen
        repeat(60) { a.process(silence, 800, Synth.RATE, t, frame); t += 16_666_667 }
        assertTrue(a.spectrum.peaks[band] < peakBefore * 0.3f)
    }
}
