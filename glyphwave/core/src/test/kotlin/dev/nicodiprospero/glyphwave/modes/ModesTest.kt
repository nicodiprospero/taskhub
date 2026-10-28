package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.audio.AudioAnalyzer
import dev.nicodiprospero.glyphwave.audio.Synth
import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import dev.nicodiprospero.glyphwave.glyph.OutputStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {

    private val playing = MediaState(access = MediaState.Access.OK, playback = MediaState.Playback.PLAYING)

    private fun liveFrame(): AudioFrame {
        val a = AudioAnalyzer()
        val f = AudioFrame()
        val block = FloatArray(800)
        // 93 blocks = 1.55 s: 50 ms into a hat/kick, so every range (incl. highs) has signal.
        repeat(93) { k ->
            Synth.track(k * 800L, block, toneHz = 220f)
            a.process(block, 800, Synth.RATE, (k + 1) * 16_666_667L, f)
        }
        return f
    }

    @Test
    fun `every mode and focus draws something, in range, inside the mask`() {
        val r = MatrixRenderer(MatrixSpec.DEFAULT_LENGTH)
        val out = IntArray(r.length * r.length)
        val frame = liveFrame()
        val tMs = frame.timestampNanos / 1_000_000
        for (m in r.modes.indices) for (focus in BandFocus.entries) {
            r.selectMode(r.modes[m].id)
            r.focus = focus
            r.render(frame, playing, tMs, out)
            assertTrue("${r.currentMode.id}/$focus idle?", !r.idleShown)
            assertTrue("${r.currentMode.id}/$focus empty", out.any { it > 0 })
            out.forEachIndexed { i, v ->
                assertTrue(v in 0..MatrixSpec.MAX_VALUE)
                if (!r.mask.contains(i)) assertEquals(0, v)
            }
        }
    }

    @Test
    fun `inactive audio shows idle with the engine hint`() {
        val r = MatrixRenderer(MatrixSpec.DEFAULT_LENGTH)
        val out = IntArray(r.length * r.length)
        r.render(AudioFrame(), playing, 1_000, out)
        assertTrue(r.idleShown)
        assertTrue(out.any { it > 0 })
        // Very dim: nothing above ~15 % drive.
        assertTrue(out.max() < MatrixSpec.MAX_VALUE * 0.15)
    }

    @Test
    fun `next mode cycles`() {
        val r = MatrixRenderer(MatrixSpec.DEFAULT_LENGTH)
        val ids = (0 until r.modes.size).map { r.nextMode().id }
        assertEquals(r.modes.map { it.id }.drop(1) + r.modes.first().id, ids)
    }

    @Test
    fun `output stage applies gamma and cap`() {
        val o = OutputStage(gamma = 2.2f, brightnessCap = 0.5f)
        assertEquals(0, o.drive(0))
        assertEquals(MatrixSpec.MAX_VALUE / 2, o.drive(MatrixSpec.MAX_VALUE), 1)
        // perceived 50 % → 0.5^2.2 ≈ 21.8 % before the cap
        assertEquals((0.2176f * 0.5f * MatrixSpec.MAX_VALUE).toInt(), o.drive(MatrixSpec.MAX_VALUE / 2), 4)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("expected $expected ± $tolerance, was $actual", kotlin.math.abs(expected - actual) <= tolerance)
}
