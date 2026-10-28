package dev.nicodiprospero.glyphwave.audio

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatDetectorTest {

    /** Runs [seconds] of the synth track through the analyser at [blockSize]/[rate] blocks; returns beat times (ms). */
    private fun beats(seconds: Int, blockSize: Int, hats: Boolean = true, kick: Boolean = true, toneHz: Float = 0f): List<Long> {
        val a = AudioAnalyzer()
        val frame = AudioFrame()
        val block = FloatArray(blockSize)
        val out = mutableListOf<Long>()
        val blocks = seconds * Synth.RATE / blockSize
        for (k in 0 until blocks) {
            val start = k.toLong() * blockSize
            Synth.track(start, block, kick = kick, hats = hats, toneHz = toneHz)
            val t = (start + blockSize) * 1_000_000_000L / Synth.RATE
            a.process(block, blockSize, Synth.RATE, t, frame)
            if (frame.beat) out += t / 1_000_000
        }
        return out
    }

    @Test
    fun `120 bpm kick gives a beat every 500 ms (playback capture rate)`() {
        val b = beats(seconds = 10, blockSize = 800)
        // 20 kicks in 10 s; the first one or two may be missed while history fills.
        assertTrue("beats=${b.size}", b.size in 17..20)
        val gaps = b.zipWithNext { x, y -> y - x }
        assertTrue("gaps $gaps", gaps.all { it in 450..550 })
    }

    @Test
    fun `120 bpm kick also works at the Visualizer rate (1024 samples, ~20 Hz)`() {
        // Visualizer: 1024-sample blocks but only ~20 blocks/s → feed every other 1024 block's worth? Here we
        // keep it simple: contiguous 2400-sample hops (20 Hz) analysed with the newest 1024 samples.
        val b = beats(seconds = 10, blockSize = 2_400)
        assertTrue("beats=${b.size}", b.size in 17..20)
    }

    @Test
    fun `hi-hats alone never trigger bass beats`() {
        assertEquals(emptyList<Long>(), beats(seconds = 6, blockSize = 800, kick = false, hats = true))
    }

    @Test
    fun `steady bass tone is not a beat`() {
        assertTrue(beats(seconds = 6, blockSize = 800, kick = false, hats = false, toneHz = 60f).size <= 1)
    }
}
