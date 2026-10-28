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

    /** Visualizer polled at 60 Hz: overlapping 1024-sample windows every 800 samples. */
    @Test
    fun `120 bpm kick with overlapping 60 Hz Visualizer polling`() {
        val a = AudioAnalyzer()
        val frame = AudioFrame()
        val window = FloatArray(1024)
        val beats = mutableListOf<Long>()
        val hops = 10 * Synth.RATE / 800
        for (k in 1..hops) {
            val end = k.toLong() * 800
            Synth.track(end - 1024, window)
            val t = end * 1_000_000_000L / Synth.RATE
            a.process(window, 1024, Synth.RATE, t, frame)
            if (frame.beat) beats += t / 1_000_000
        }
        assertTrue("beats=${beats.size}", beats.size in 17..20)
        assertTrue(beats.zipWithNext { x, y -> y - x }.all { it in 450..550 })
    }

    /** Hip-hop: an 808 that sustains for the whole beat, with a new note on every beat. */
    @Test
    fun `sustained 808 notes still give one beat per note`() {
        val a = AudioAnalyzer()
        val frame = AudioFrame()
        val block = FloatArray(800)
        val beats = mutableListOf<Long>()
        val beatLen = Synth.RATE / 2 // 120 bpm
        val notes = floatArrayOf(45f, 45f, 52f, 41f)
        for (k in 0 until 10 * Synth.RATE / 800) {
            for (i in 0 until 800) {
                val n = k.toLong() * 800 + i
                val note = notes[((n / beatLen) % notes.size).toInt()]
                val since = (n % beatLen).toFloat() / Synth.RATE
                // fast attack, slow decay: still ~60 % at the next note
                val env = (since / 0.005f).coerceAtMost(1f) * kotlin.math.exp(-since * 1f)
                block[i] = 0.7f * env * kotlin.math.sin(2 * Math.PI * note * n / Synth.RATE).toFloat()
            }
            val t = (k + 1L) * 800 * 1_000_000_000L / Synth.RATE
            a.process(block, 800, Synth.RATE, t, frame)
            if (frame.beat) beats += t / 1_000_000
        }
        assertTrue("beats=${beats.size} $beats", beats.size in 16..20)
    }
}
