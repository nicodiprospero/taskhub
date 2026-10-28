package dev.nicodiprospero.glyphwave.engine

import dev.nicodiprospero.glyphwave.debug.GlyphEventLog
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import dev.nicodiprospero.glyphwave.glyph.TestPattern
import dev.nicodiprospero.glyphwave.modes.MatrixRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide shared state between the toy service, the (future) audio engine / media bridge and
 * the companion UI. Everything here is a StateFlow so any component can observe without coupling.
 */
object Engine {

    enum class ToyState { UNBOUND, BINDING, CONNECTED, DISCONNECTED }

    data class ToyStatus(
        val state: ToyState = ToyState.UNBOUND,
        /** Matrix side length reported by the SDK at runtime (null until connected). */
        val matrixLength: Int? = null,
        val registered: Boolean = false,
        /** Last error from the SDK (e.g. GlyphException), shown on the debug screen. */
        val lastError: String? = null,
    )

    /** Raw Glyph Button + lifecycle events, for gesture instrumentation (Phase 0). */
    val glyphEvents = GlyphEventLog()

    private val _toyStatus = MutableStateFlow(ToyStatus())
    val toyStatus: StateFlow<ToyStatus> = _toyStatus.asStateFlow()

    /**
     * Diagnostic pattern shown by the toy instead of the visualizer; null = visualizer (default).
     * Selected from the debug screen; while one is shown, long press cycles patterns.
     */
    val testPattern = MutableStateFlow<TestPattern?>(null)

    /** Render rate of the toy (30 default, 60 optional — settings). */
    @Volatile var fps: Int = 30

    /** Latest analysed audio (written by the engine per block, read by render loops). */
    val audioFrames = AudioFrameExchange()

    /** Exactly what was last sent to setMatrixFrame — feeds the in-app live preview. */
    val matrixFrame = MatrixFrameBuffer(MatrixSpec.DEFAULT_LENGTH)

    private var renderer: MatrixRenderer? = null

    /** The single renderer shared by the toy and the in-app preview (so they never disagree). */
    @Synchronized
    fun renderer(length: Int = MatrixSpec.DEFAULT_LENGTH): MatrixRenderer {
        renderer?.let { if (it.length == length) return it }
        return MatrixRenderer(length).also { r ->
            renderer?.let { old ->
                r.selectMode(old.currentMode.id)
                r.focus = old.focus
                r.output = old.output
            }
            renderer = r
        }
    }

    private val _audio = MutableStateFlow(AudioStatus())

    /** Audio engine snapshot (~10 Hz) for UI and logic. */
    val audio: StateFlow<AudioStatus> = _audio.asStateFlow()

    /** Latest auto-gained level 0..1, written per audio block, read by the toy's render loop. */
    @Volatile var audioLevel: Float = 0f

    fun publishAudio(status: AudioStatus) {
        _audio.value = status
    }

    fun updateToyStatus(transform: (ToyStatus) -> ToyStatus) {
        _toyStatus.value = transform(_toyStatus.value)
    }
}

/** Latest matrix frame with a version counter. Allocation-free copy in/out. */
class MatrixFrameBuffer(length: Int) {
    private val lock = Any()
    private var pixels = IntArray(length * length)

    @Volatile var version = 0L
        private set

    /** Wall-clock (elapsedRealtime) of the last publish, to know whether the toy is actively rendering. */
    @Volatile var publishedAtMs = 0L
        private set

    fun publish(frame: IntArray, nowMs: Long) = synchronized(lock) {
        if (pixels.size != frame.size) pixels = IntArray(frame.size)
        frame.copyInto(pixels)
        version++
        publishedAtMs = nowMs
    }

    /** Copies into [dst] if sizes match; returns the version read. */
    fun readInto(dst: IntArray): Long = synchronized(lock) {
        if (dst.size == pixels.size) pixels.copyInto(dst)
        version
    }
}
