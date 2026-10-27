package dev.nicodiprospero.glyphwave.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One raw event as received (or a lifecycle marker), stamped with a monotonic clock. */
data class GlyphEvent(
    val seq: Int,
    /** Monotonic timestamp in nanoseconds (elapsedRealtimeNanos on device). */
    val tNanos: Long,
    /** Milliseconds since the previous event, or -1 for the first one. */
    val deltaMs: Double,
    val kind: Kind,
    val name: String,
) {
    enum class Kind { BUTTON, LIFECYCLE, UNKNOWN }

    val tMs: Double get() = tNanos / 1_000_000.0

    fun format(): String =
        "#%03d t=%.1fms Δ=%s %s %s".format(
            seq, tMs, if (deltaMs < 0) "—" else "%.1fms".format(deltaMs), kind, name,
        )
}

/**
 * Bounded in-memory log of raw Glyph events, shared by the toy service (writer) and the companion
 * app's debug screen (reader). Thread-safe; emits immutable snapshots.
 */
class GlyphEventLog(private val capacity: Int = 300) {

    private val lock = Any()
    private val buffer = ArrayDeque<GlyphEvent>(capacity)
    private var seq = 0
    private var lastNanos = Long.MIN_VALUE

    private val _events = MutableStateFlow<List<GlyphEvent>>(emptyList())
    val events: StateFlow<List<GlyphEvent>> = _events.asStateFlow()

    fun record(tNanos: Long, kind: GlyphEvent.Kind, name: String): GlyphEvent = synchronized(lock) {
        val delta = if (lastNanos == Long.MIN_VALUE) -1.0 else (tNanos - lastNanos) / 1_000_000.0
        lastNanos = tNanos
        val event = GlyphEvent(++seq, tNanos, delta, kind, name)
        if (buffer.size == capacity) buffer.removeFirst()
        buffer.addLast(event)
        _events.value = buffer.toList()
        event
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
        seq = 0
        lastNanos = Long.MIN_VALUE
        _events.value = emptyList()
    }

    /** Plain-text dump (oldest first) for "copy to clipboard" / bug reports. */
    fun dump(): String = events.value.joinToString("\n") { it.format() }
}
