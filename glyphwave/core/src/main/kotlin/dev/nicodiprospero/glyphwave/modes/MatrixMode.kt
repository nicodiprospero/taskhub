package dev.nicodiprospero.glyphwave.modes

import dev.nicodiprospero.glyphwave.audio.SpectrumProcessor
import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/**
 * A visualizer mode. Draws PERCEIVED brightness (0..1 via [MatrixCanvas.set]); gamma, brightness cap
 * and the circle mask are applied afterwards by the renderer. Must not allocate in [render].
 *
 * (Deviation from the brief's `out: IntArray`: modes draw into a [MatrixCanvas] wrapping that array —
 * same data, fewer index bugs. See DECISIONS.md D-026.)
 */
interface MatrixMode {
    val id: String
    val name: String
    fun render(audio: AudioFrame, media: MediaState, tMs: Long, canvas: MatrixCanvas)
    fun reset() {}
}

/** Which part of the spectrum the spectrum modes zoom into. */
enum class BandFocus(val label: String, val fLo: Float, val fHi: Float) {
    FULL("Full", AudioFrame.BAND_F_MIN, AudioFrame.BAND_F_MAX),
    BASS("808s", 40f, 250f),
    MIDS("Mids", 250f, 4_000f),
    HIGHS("Highs", 4_000f, AudioFrame.BAND_F_MAX),
}

/**
 * Resamples the frame's 32 full-range bands to [count] bands covering one [BandFocus] range (log
 * interpolation). For a zoomed focus it also re-gains locally: band values are dB-linear, so the view keeps
 * a slowly decaying running max of its own range and maps the top [LOCAL_RANGE] (≈ 23 dB) of it to 0..1.
 * Without this, "808s" saturates (bass dominates the global gain) and "Highs" barely moves (hats sit
 * ~20 dB below the kick).
 */
class BandView(val count: Int) {
    val values = FloatArray(count)
    val peaks = FloatArray(count)
    private val srcPos = FloatArray(count)
    private var configured: BandFocus? = null
    private var runningMax = 0f
    private var lastTMs = -1L

    fun update(audio: AudioFrame, focus: BandFocus, tMs: Long) {
        if (configured != focus) {
            configured = focus
            // A new range has its own level: don't carry the old range's gain over (808s → Highs went dark).
            runningMax = 0f
            lastTMs = -1L
            for (j in 0 until count) {
                val f = focus.fLo * (focus.fHi / focus.fLo).pow((j + 0.5f) / count)
                srcPos[j] = SpectrumProcessor.bandIndexOf(f, AudioFrame.BAND_F_MIN, AudioFrame.BAND_F_MAX, audio.bandCount) - 0.5f
            }
        }
        val last = audio.bandCount - 1
        for (j in 0 until count) {
            val p = srcPos[j].coerceIn(0f, last.toFloat())
            val i0 = floor(p).toInt().coerceAtMost(last - 1)
            val t = p - i0
            values[j] = audio.bands[i0] * (1 - t) + audio.bands[i0 + 1] * t
            peaks[j] = audio.peaks[i0] * (1 - t) + audio.peaks[i0 + 1] * t
        }
        if (focus == BandFocus.FULL) return

        val dt = if (lastTMs < 0) 0f else ((tMs - lastTMs) / 1000f).coerceIn(0f, 0.25f)
        lastTMs = tMs
        var frameMax = 0f
        for (j in 0 until count) if (values[j] > frameMax) frameMax = values[j]
        runningMax = maxOf(frameMax, runningMax - LOCAL_DECAY_PER_S * dt, MIN_SPAN)
        // Window = top LOCAL_RANGE below the running max, but never below the global floor (0): a quiet
        // range (hats) is stretched up instead of lifting its noise floor.
        val floor = maxOf(runningMax - LOCAL_RANGE, 0f)
        val span = runningMax - floor
        for (j in 0 until count) {
            values[j] = ((values[j] - floor) / span).coerceIn(0f, 1f)
            peaks[j] = ((peaks[j] - floor) / span).coerceIn(0f, 1f)
        }
    }

    private companion object {
        const val LOCAL_RANGE = 0.55f
        const val LOCAL_DECAY_PER_S = 0.15f
        const val MIN_SPAN = 0.12f
    }
}

/** 1 right after a beat, decaying exponentially with [tauMs]. */
fun beatEnvelope(audio: AudioFrame, tMs: Long, tauMs: Float = 140f): Float {
    val sinceMs = (tMs * 1_000_000 - audio.lastBeatNanos) / 1_000_000f
    if (sinceMs < 0f || sinceMs > tauMs * 6) return 0f
    return audio.beatStrength * exp(-sinceMs / tauMs)
}
