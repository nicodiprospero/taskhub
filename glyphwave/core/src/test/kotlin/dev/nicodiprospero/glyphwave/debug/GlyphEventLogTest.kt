package dev.nicodiprospero.glyphwave.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class GlyphEventLogTest {

    @Test
    fun `records deltas between events`() {
        val log = GlyphEventLog()
        log.record(1_000_000_000, GlyphEvent.Kind.BUTTON, "action_down")
        log.record(1_650_000_000, GlyphEvent.Kind.BUTTON, "change")
        log.record(1_700_500_000, GlyphEvent.Kind.BUTTON, "action_up")
        val e = log.events.value
        assertEquals(listOf(1, 2, 3), e.map { it.seq })
        assertEquals(-1.0, e[0].deltaMs, 0.0)
        assertEquals(650.0, e[1].deltaMs, 1e-9)
        assertEquals(50.5, e[2].deltaMs, 1e-9)
    }

    @Test
    fun `is bounded and drops oldest`() {
        val log = GlyphEventLog(capacity = 3)
        repeat(5) { log.record(it * 1_000_000L, GlyphEvent.Kind.BUTTON, "e$it") }
        assertEquals(listOf("e2", "e3", "e4"), log.events.value.map { it.name })
    }

    @Test
    fun `clear resets sequence and delta`() {
        val log = GlyphEventLog()
        log.record(5, GlyphEvent.Kind.LIFECYCLE, "bind")
        log.clear()
        val e = log.record(10, GlyphEvent.Kind.BUTTON, "change")
        assertEquals(1, e.seq)
        assertEquals(-1.0, e.deltaMs, 0.0)
    }

    @Test
    fun `format is stable`() {
        val log = GlyphEventLog()
        val e = log.record(1_234_500_000, GlyphEvent.Kind.BUTTON, "change")
        assertEquals("#001 t=1234.5ms Δ=— BUTTON change", e.format())
    }
}
