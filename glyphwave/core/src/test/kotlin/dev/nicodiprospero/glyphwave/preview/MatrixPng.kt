package dev.nicodiprospero.glyphwave.preview

import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow

/**
 * Renders a raw matrix frame the way it looks on the phone: round dots on black, cells without a
 * physical LED not drawn, unlit LEDs as a faint grey ring so the circle stays readable.
 */
object MatrixPng {

    fun write(frame: IntArray, mask: CircleMask, file: File, cell: Int = 24, caption: String? = null) {
        val n = mask.length
        val pad = cell
        val captionH = if (caption != null) cell else 0
        val size = n * cell + pad * 2
        val img = BufferedImage(size, size + captionH, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fillRect(0, 0, img.width, img.height)
        val dot = (cell * 0.78).toInt()
        val off = (cell - dot) / 2
        for (y in 0 until n) for (x in 0 until n) {
            if (!mask.contains(x, y)) continue
            val v = frame[y * n + x].coerceIn(0, MatrixSpec.MAX_VALUE) / MatrixSpec.MAX_VALUE.toFloat()
            val px = pad + x * cell + off
            val py = pad + y * cell + off
            if (v <= 0f) {
                g.color = Color(28, 28, 28)
                g.fillOval(px, py, dot, dot)
            } else {
                // LEDs look brighter than linear on a screen: show perceived level (inverse gamma).
                val c = (40 + 215 * v.toDouble().pow(1 / 2.2)).toInt().coerceIn(0, 255)
                g.color = Color(c, c, c)
                g.fillOval(px, py, dot, dot)
            }
        }
        if (caption != null) {
            g.color = Color(200, 200, 200)
            g.font = g.font.deriveFont(cell * 0.55f)
            g.drawString(caption, pad, size + captionH / 2)
        }
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
    }

    fun outputDir(): File =
        File(System.getProperty("glyphwave.previewDir") ?: "build/matrix-previews")
}
