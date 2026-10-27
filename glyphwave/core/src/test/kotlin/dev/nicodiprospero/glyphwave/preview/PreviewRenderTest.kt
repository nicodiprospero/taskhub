package dev.nicodiprospero.glyphwave.preview

import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.Logo
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import dev.nicodiprospero.glyphwave.glyph.TestPattern
import dev.nicodiprospero.glyphwave.glyph.TestPatterns
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
        println("Previews written to ${dir.absolutePath}")
    }
}
