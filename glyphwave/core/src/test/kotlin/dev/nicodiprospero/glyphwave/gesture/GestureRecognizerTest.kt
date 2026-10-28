package dev.nicodiprospero.glyphwave.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timelines replay the owner's real Phase 0 captures (DECISIONS.md, observed event table): change ≈ 500 ms
 * after down, release 200–340 ms later, fastest release→press gap 368 ms.
 */
class GestureRecognizerTest {

    private val gestures = mutableListOf<Pair<Gesture, Long>>()
    private val feedback = mutableListOf<GestureFeedback>()
    private val r = GestureRecognizer(onGesture = { g, t -> gestures += g to t }, onFeedback = { f, _ -> feedback += f })

    /** Plays a script of (time, event) and advances the clock every 10 ms up to [until]. */
    private fun play(until: Long, vararg events: Pair<Long, String>) {
        val byTime = events.groupBy({ it.first }, { it.second })
        var t = 0L
        while (t <= until) {
            byTime[t]?.forEach {
                when (it) {
                    "down" -> r.onDown(t)
                    "change" -> r.onChange(t)
                    "up" -> r.onUp(t)
                    "unbind" -> r.reset(t)
                }
            }
            r.advance(t)
            t += 10
        }
    }

    private fun longPress(start: Long, release: Long = 300) =
        arrayOf(start to "down", start + 500 to "change", start + 500 + release to "up")

    @Test
    fun `single long press fires after the multi-press gap`() {
        play(3_000, *longPress(0, 300))
        assertEquals(listOf(Gesture.LONG_PRESS to 1_400L), gestures)
        assertEquals(listOf(GestureFeedback.PRESS_CONFIRMED), feedback)
    }

    @Test
    fun `double long press from the real capture (368 ms gap)`() {
        // down 0, change 500, up 840 | down 1208, change 1711, up 1967 → rounded to the 10 ms grid
        play(4_000, 0L to "down", 500L to "change", 840L to "up", 1_210L to "down", 1_710L to "change", 1_970L to "up")
        assertEquals(listOf(Gesture.DOUBLE_LONG_PRESS to 2_570L), gestures)
    }

    @Test
    fun `triple long press fires immediately on the third release`() {
        play(5_000, *longPress(0), *longPress(1_200), *longPress(2_400))
        assertEquals(listOf(Gesture.TRIPLE_LONG_PRESS to 3_200L), gestures)
    }

    @Test
    fun `presses too far apart are separate single long presses`() {
        play(5_000, *longPress(0), *longPress(1_500))
        assertEquals(listOf(Gesture.LONG_PRESS, Gesture.LONG_PRESS), gestures.map { it.first })
    }

    @Test
    fun `3 s hold is HOLD_MEDIUM on release, with a threshold tick at 2 s`() {
        play(5_000, 0L to "down", 500L to "change", 3_000L to "up")
        assertEquals(listOf(Gesture.HOLD_MEDIUM to 3_000L), gestures)
        assertEquals(listOf(GestureFeedback.PRESS_CONFIRMED, GestureFeedback.HOLD_MEDIUM_REACHED), feedback)
    }

    @Test
    fun `6 s hold is HOLD_LONG at the 4 s threshold and nothing on release`() {
        play(8_000, 0L to "down", 500L to "change", 5_990L to "up")
        assertEquals(listOf(Gesture.HOLD_LONG to 4_000L), gestures)
    }

    @Test
    fun `system short press (down then unbind) does nothing`() {
        play(2_000, 0L to "down", 210L to "unbind")
        assertTrue(gestures.isEmpty())
        assertFalse(r.isPressed)
    }

    @Test
    fun `down and up without change does nothing`() {
        play(2_000, 0L to "down", 200L to "up")
        assertTrue(gestures.isEmpty())
    }

    @Test
    fun `unbind mid-gesture drops pending taps`() {
        play(3_000, *longPress(0), 900L to "unbind")
        assertTrue(gestures.isEmpty())
        assertTrue(GestureFeedback.CANCELLED in feedback)
    }

    @Test
    fun `tap followed by a hold discards the tap`() {
        play(6_000, *longPress(0), 1_000L to "down", 1_500L to "change", 3_500L to "up")
        assertEquals(listOf(Gesture.HOLD_MEDIUM to 3_500L), gestures)
    }

    @Test
    fun `progress ring runs from down to the long threshold`() {
        r.onDown(0)
        assertEquals(-1f, r.holdProgress(300), 0f) // not confirmed yet
        r.onChange(500)
        assertEquals(0.25f, r.holdProgress(1_000), 1e-6f)
        assertEquals(0.5f, r.mediumMark, 1e-6f)
        r.advance(4_000)
        assertEquals(-1f, r.holdProgress(4_100), 0f) // long fired → ring done
    }

    @Test
    fun `change-only fallback counts changes and disables holds`() {
        play(4_000, 500L to "change", 1_500L to "change")
        assertFalse(r.supportsHolds)
        assertEquals(listOf(Gesture.DOUBLE_LONG_PRESS to 2_700L), gestures)
    }

    @Test
    fun `timings are configurable`() {
        r.timings = GestureTimings(multiGapMs = 300, holdMediumMs = 1_000, holdLongMs = 2_000)
        play(4_000, 0L to "down", 500L to "change", 1_500L to "up")
        assertEquals(listOf(Gesture.HOLD_MEDIUM to 1_500L), gestures)
    }

    @Test
    fun `default mapping matches the brief`() {
        assertEquals(GestureAction.NEXT_MODE, GestureAction.DEFAULTS[Gesture.LONG_PRESS])
        assertEquals(GestureAction.NEXT_TRACK, GestureAction.DEFAULTS[Gesture.DOUBLE_LONG_PRESS])
        assertEquals(GestureAction.PREVIOUS_TRACK, GestureAction.DEFAULTS[Gesture.TRIPLE_LONG_PRESS])
        assertEquals(GestureAction.PLAY_PAUSE, GestureAction.DEFAULTS[Gesture.HOLD_MEDIUM])
        assertEquals(GestureAction.NOW_PLAYING, GestureAction.DEFAULTS[Gesture.HOLD_LONG])
    }
}
