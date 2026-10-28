package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.Polar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Idle (paused / no audio): a slow breathing dot at very low brightness. When the audio engine is not
 * running, a short dim arc at the bottom rim hints "start the engine from the app".
 */
class IdleMode(private val polar: Polar) : MatrixMode {
    override val id = "idle"
    override val name = "Idle"

    /** Set by the renderer every frame. */
    var engineRunning = false

    override fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas) {
        val breath = 0.5f + 0.5f * sin(2 * PI * tMs / PERIOD_MS).toFloat()
        val level = 0.07f + 0.10f * breath
        val radius = 1.1f + 0.6f * breath
        val px = canvas.pixels
        for (i in px.indices) {
            val r = polar.radius[i]
            var v = if (r <= radius) level else 0f
            if (!engineRunning && r > polar.maxRadius - 1.2f && abs(polar.turn[i] - 0.5f) < 0.06f) v = 0.12f
            px[i] = canvas.toValue(v)
        }
    }

    private companion object {
        const val PERIOD_MS = 4_000.0
    }
}
