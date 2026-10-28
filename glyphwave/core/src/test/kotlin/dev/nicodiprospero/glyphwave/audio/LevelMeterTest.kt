package dev.nicodiprospero.glyphwave.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LevelMeterTest {

    private val block = 800 // 60 Hz at 48 kHz
    private val stepNanos = 16_666_667L

    private fun sine(amp: Float, out: FloatArray) {
        for (i in out.indices) out[i] = amp * sin(2 * PI * 440 * i / 48_000).toFloat()
    }

    @Test
    fun `rms of a full-scale sine is about 0_707`() {
        val m = LevelMeter()
        val buf = FloatArray(block).also { sine(1f, it) }
        m.process(buf, block, 0)
        assertEquals(0.707f, m.rms, 0.01f)
        assertEquals(1f, m.peak, 0.01f)
    }

    @Test
    fun `measures block rate`() {
        val m = LevelMeter()
        val buf = FloatArray(block).also { sine(0.5f, it) }
        repeat(120) { m.process(buf, block, it * stepNanos) }
        assertEquals(60f, m.blockRate, 1f)
    }

    @Test
    fun `accumulates silence and resets on signal`() {
        val m = LevelMeter()
        val zeros = FloatArray(block)
        repeat(150) { m.process(zeros, block, it * stepNanos) } // ~2.5 s
        assertTrue("silent ${m.silentForMs}", m.silentForMs in 2450..2500) // 149 intervals × 16.67 ms
        val buf = FloatArray(block).also { sine(0.3f, it) }
        m.process(buf, block, 150 * stepNanos)
        assertEquals(0L, m.silentForMs)
    }

    @Test
    fun `auto gain makes quiet and loud tracks both reach full level`() {
        for (amp in listOf(0.05f, 0.9f)) {
            val m = LevelMeter()
            val buf = FloatArray(block).also { sine(amp, it) }
            repeat(300) { m.process(buf, block, it * stepNanos) }
            assertTrue("amp $amp level ${m.level}", m.level > 0.95f)
        }
    }
}
