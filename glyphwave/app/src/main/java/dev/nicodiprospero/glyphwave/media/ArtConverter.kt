package dev.nicodiprospero.glyphwave.media

import android.graphics.Bitmap
import dev.nicodiprospero.glyphwave.engine.ArtPixels
import kotlin.math.min

/** Album art → centre-cropped square luminance grid for the matrix modes (runs off the main thread). */
object ArtConverter {

    /** Big enough to dither down to the matrix with detail to spare; tiny in memory (16 KB). */
    const val SIZE = 64

    fun toLuma(source: Bitmap): ArtPixels {
        val src = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val side = min(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        val argb = IntArray(SIZE * SIZE)
        scaled.getPixels(argb, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== cropped && scaled !== src) scaled.recycle()
        if (cropped !== src) cropped.recycle()
        if (src !== source) src.recycle()
        val luma = IntArray(SIZE * SIZE) { i ->
            val c = argb[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
        return ArtPixels(SIZE, luma)
    }
}
