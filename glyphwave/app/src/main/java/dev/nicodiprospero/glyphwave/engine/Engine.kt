package dev.nicodiprospero.glyphwave.engine

import dev.nicodiprospero.glyphwave.debug.GlyphEventLog
import dev.nicodiprospero.glyphwave.glyph.TestPattern
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

    /** Diagnostic pattern shown by the toy. Long press on the Glyph Button or the app changes it. */
    val testPattern = MutableStateFlow(TestPattern.VALUE_RANGE)

    fun updateToyStatus(transform: (ToyStatus) -> ToyStatus) {
        _toyStatus.value = transform(_toyStatus.value)
    }
}
