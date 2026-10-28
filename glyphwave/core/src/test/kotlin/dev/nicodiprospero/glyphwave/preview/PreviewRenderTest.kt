package dev.nicodiprospero.glyphwave.preview

import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.Logo
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import dev.nicodiprospero.glyphwave.glyph.TestPattern
import dev.nicodiprospero.glyphwave.glyph.TestPatterns
import dev.nicodiprospero.glyphwave.audio.AudioAnalyzer
import dev.nicodiprospero.glyphwave.audio.Synth
import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.modes.BandFocus
import dev.nicodiprospero.glyphwave.modes.MatrixRenderer
import org.junit.Test
import java.io.File

/** Run via `./gradlew :core:renderPreviews` — excluded from the normal `test` task. */
class PreviewRenderTest {

    private val mask = CircleMask(MatrixSpec.DEFAULT_LENGTH)
    private val canvas = MatrixCanvas(MatrixSpec.DEFAULT_LENGTH)

    @Test
    fun renderTestPatterns() {
        val dir = MatrixPng.outputDir()
        for (p in TestPattern.entries) {
            TestPatterns.render(p, canvas, mask)
            MatrixPng.write(canvas.pixels, mask, File(dir, "test-${p.name.lowercase()}.png"), caption = p.title)
        }
        val all = IntArray(mask.length * mask.length) { MatrixSpec.MAX_VALUE }
        mask.apply(all)
        MatrixPng.write(all, mask, File(dir, "test-all-leds.png"), caption = "All ${mask.ledCount} LEDs")
        Logo.render(canvas, mask)
        MatrixPng.write(canvas.pixels, mask, File(dir, "logo.png"), caption = "Toy preview / logo")
        // Copy these two into app/src/main/res/drawable/ when the logo changes.
        VectorDrawables.toyPreview(canvas.pixels, mask, File(dir, "drawable/toy_preview.xml"))
        VectorDrawables.launcherForeground(canvas.pixels, mask, File(dir, "drawable/ic_launcher_foreground.xml"))
        renderModes(dir)
        println("Previews written to ${dir.absolutePath}")
    }

    /**
     * Runs the synthetic 120 BPM track (kick + hats + 220 Hz bass line) through the real analyser and renders
     * every mode at four moments of a beat: right on the kick, +120 ms, +250 ms (8th-note hat), +400 ms.
     * Output: one contact sheet per mode (+ focus variants for the spectrum modes) and the idle frame.
     */
    private fun renderModes(dir: File) {
        val playing = MediaState(access = MediaState.Access.OK, playback = MediaState.Playback.PLAYING)
        val momentsMs = longArrayOf(2_010, 2_120, 2_250, 2_400)
        val variants = mutableListOf<Pair<String, BandFocus>>()
        val probe = MatrixRenderer(mask.length)
        for (m in probe.modes) {
            variants += m.id to BandFocus.FULL
            if (m.id == "radial" || m.id == "bars") {
                variants += m.id to BandFocus.BASS
                variants += m.id to BandFocus.HIGHS
            }
        }
        for ((modeId, focus) in variants) {
            val renderer = MatrixRenderer(mask.length).also { it.selectMode(modeId); it.focus = focus }
            val analyzer = AudioAnalyzer()
            val frame = AudioFrame()
            val block = FloatArray(800)
            val out = IntArray(mask.length * mask.length)
            val frames = mutableListOf<IntArray>()
            var k = 0L
            for (moment in momentsMs) {
                while ((k + 1) * 800 * 1000 / Synth.RATE <= moment) {
                    Synth.track(k * 800, block, toneHz = 220f)
                    val tNanos = (k + 1) * 800 * 1_000_000_000L / Synth.RATE
                    analyzer.process(block, 800, Synth.RATE, tNanos, frame)
                    // Render every block so stateful modes (pulse waves, peaks) evolve as on device.
                    renderer.render(frame, playing, tNanos / 1_000_000, out)
                    k++
                }
                frames += out.copyOf()
            }
            val name = if (focus == BandFocus.FULL) "mode-$modeId" else "mode-$modeId-${focus.name.lowercase()}"
            MatrixPng.sheet(frames, mask, File(dir, "$name.png"), "$modeId · ${focus.label} · kick +0/120/250/400 ms")
        }
        val idle = MatrixRenderer(mask.length)
        val out = IntArray(mask.length * mask.length)
        idle.render(AudioFrame(), playing, 1_000, out)
        MatrixPng.write(out, mask, File(dir, "mode-idle-engine-off.png"), caption = "Idle (engine off hint)")
    }
}
