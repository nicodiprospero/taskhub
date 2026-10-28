package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.OutputStage
import dev.nicodiprospero.glyphwave.glyph.Polar

/**
 * The one place a matrix frame is produced — used by the Glyph Toy, the in-app live preview and the
 * JVM PNG previews, so all three always agree. Mode → mask → gamma/brightness LUT → [out].
 * Allocation-free per frame. Thread-safe (synchronized; one caller at a time in practice).
 */
class MatrixRenderer(val length: Int) {

    val mask = CircleMask(length)
    private val polar = Polar(length)
    private val canvas = MatrixCanvas(length)

    private val radial = RadialSpectrumMode(polar)
    private val bars = ClassicBarsMode(mask)
    val modes: List<MatrixMode> = listOf(radial, bars, PulseMode(polar, mask), BassTrebleMode(polar))
    private val idle = IdleMode(polar)

    @Volatile var modeIndex = 0
        private set

    @Volatile var focus = BandFocus.FULL

    @Volatile var output = OutputStage()

    /** Show idle after this long of (near) silence even if the engine runs. */
    var idleAfterSilenceMs = 1_500L

    private var silentSinceMs = -1L

    /** True if the last frame showed the idle animation. */
    @Volatile var idleShown = true
        private set

    val currentMode: MatrixMode get() = modes[modeIndex]

    fun selectMode(id: String): Boolean {
        val i = modes.indexOfFirst { it.id == id }
        if (i < 0) return false
        modeIndex = i
        return true
    }

    fun nextMode(): MatrixMode {
        modeIndex = (modeIndex + 1) % modes.size
        return currentMode
    }

    @Synchronized
    fun render(audio: AudioFrame, media: MediaState, tMs: Long, out: IntArray) {
        val quiet = !audio.active || audio.rms < SILENCE_RMS
        if (quiet) {
            if (silentSinceMs < 0) silentSinceMs = tMs
        } else {
            silentSinceMs = -1
        }
        val showIdle = !audio.active || (quiet && tMs - silentSinceMs >= idleAfterSilenceMs)
        idleShown = showIdle

        canvas.clear()
        if (showIdle) {
            idle.engineRunning = audio.active
            idle.render(audio, media, tMs, canvas)
        } else {
            radial.focus = focus
            bars.focus = focus
            currentMode.render(audio, media, tMs, canvas)
        }
        mask.apply(canvas.pixels)
        output.apply(canvas.pixels, out)
    }

    private companion object {
        const val SILENCE_RMS = 0.002f
    }
}
