package dev.nicodiprospero.glyphwave.gesture

/** Recognised Glyph Button gestures (DECISIONS.md D-015 / D-032). */
enum class Gesture { LONG_PRESS, DOUBLE_LONG_PRESS, TRIPLE_LONG_PRESS, HOLD_MEDIUM, HOLD_LONG }

/** Non-action feedback moments (haptic ticks, progress ring markers). */
enum class GestureFeedback {
    /** The system confirmed a long press (`change`): show the hold ring. */
    PRESS_CONFIRMED,
    /** A held press crossed [GestureTimings.holdMediumMs]: releasing now = HOLD_MEDIUM. */
    HOLD_MEDIUM_REACHED,
    /** Press ended without producing a gesture (system short press, unbind, cancelled taps). */
    CANCELLED,
}

/** All timings configurable (settings screen in Phase 5). Defaults from the Phase 0 measurements. */
data class GestureTimings(
    /** Release → next press gap that still counts as the same multi-press. Observed fastest: 368 ms. */
    val multiGapMs: Long = 600,
    val holdMediumMs: Long = 2_000,
    val holdLongMs: Long = 4_000,
    /** Change-only fallback (no down/up events): change → change window. */
    val changeOnlyWindowMs: Long = 1_200,
)

/**
 * Pure-Kotlin state machine for the Glyph Button, built on the OBSERVED event sequence (DECISIONS.md, Phase 0):
 * `action_down` → `change` at a fixed ~500 ms while held → `action_up` (always, after a change). A short
 * press sends `action_down` and then the toy is unbound — no up, no change.
 *
 * - Press classified on release by its down→up duration (only if `change` was seen):
 *   < holdMedium → one "tap" of a multi-press; holdMedium..holdLong → HOLD_MEDIUM on release;
 *   HOLD_LONG fires the moment holdLong is crossed (nothing longer exists, so no need to wait).
 * - Taps: after release, wait [GestureTimings.multiGapMs] for the next press; 1/2/3 taps → LONG / DOUBLE /
 *   TRIPLE (TRIPLE fires immediately on the 3rd release).
 * - A press that turns into a hold discards pending taps (holds are standalone gestures).
 * - If a `change` ever arrives without a preceding `action_down`, the recognizer switches to change-only
 *   mode (single/double/triple from `change` timing; holds unavailable — [supportsHolds] becomes false).
 *
 * No clock, no threads: the host feeds events with a monotonic time in ms and calls [advance] (each render
 * frame is enough — the toy renders at 30–60 fps). Emits through [onGesture] / [onFeedback].
 */
class GestureRecognizer(
    var timings: GestureTimings = GestureTimings(),
    private val onGesture: (Gesture, Long) -> Unit,
    private val onFeedback: (GestureFeedback, Long) -> Unit = { _, _ -> },
) {
    var supportsHolds = true
        private set

    private var pressed = false
    private var downAt = 0L
    private var sawChange = false
    private var mediumSignalled = false
    private var longFired = false
    private var taps = 0
    private var gapDeadline = Long.MAX_VALUE

    val isPressed: Boolean get() = pressed

    fun onDown(t: Long) {
        if (!supportsHolds) return
        if (pressed) return // duplicate down
        pressed = true
        downAt = t
        sawChange = false
        mediumSignalled = false
        longFired = false
        gapDeadline = Long.MAX_VALUE // a new press continues the multi-press sequence
    }

    fun onChange(t: Long) {
        if (!pressed) {
            // No down before change → device doesn't send down/up: count changes only.
            if (supportsHolds && taps == 0) supportsHolds = false
            if (!supportsHolds) {
                taps++
                if (taps >= 3) emitTaps(t) else gapDeadline = t + timings.changeOnlyWindowMs
            }
            return
        }
        if (!sawChange) {
            sawChange = true
            onFeedback(GestureFeedback.PRESS_CONFIRMED, t)
        }
    }

    fun onUp(t: Long) {
        if (!pressed) return
        pressed = false
        val d = t - downAt
        when {
            !sawChange -> cancel(t) // short press (normally followed by unbind)
            longFired -> resetSequence() // HOLD_LONG already emitted at the threshold
            d >= timings.holdMediumMs -> {
                resetSequence()
                onGesture(Gesture.HOLD_MEDIUM, t)
            }
            else -> {
                taps++
                if (taps >= 3) emitTaps(t) else gapDeadline = t + timings.multiGapMs
            }
        }
    }

    /** Toy unbound / service disconnected mid-gesture: drop everything, emit nothing. */
    fun reset(t: Long) {
        val wasActive = pressed || taps > 0
        pressed = false
        resetSequence()
        if (wasActive) onFeedback(GestureFeedback.CANCELLED, t)
    }

    /** Timers. Call often (every frame). */
    fun advance(now: Long) {
        if (pressed && sawChange && !longFired) {
            val d = now - downAt
            if (!mediumSignalled && d >= timings.holdMediumMs) {
                mediumSignalled = true
                taps = 0 // a hold is standalone: pending taps are discarded
                onFeedback(GestureFeedback.HOLD_MEDIUM_REACHED, now)
            }
            if (d >= timings.holdLongMs) {
                longFired = true
                onGesture(Gesture.HOLD_LONG, now)
            }
        }
        if (!pressed && taps > 0 && now >= gapDeadline) emitTaps(now)
    }

    /**
     * 0..1 progress toward HOLD_LONG while a confirmed long press is held (for the progress ring);
     * -1 when no ring should be shown.
     */
    fun holdProgress(now: Long): Float =
        if (pressed && sawChange && !longFired && supportsHolds) ((now - downAt).toFloat() / timings.holdLongMs).coerceIn(0f, 1f) else -1f

    /** Where HOLD_MEDIUM sits on the ring (0..1). */
    val mediumMark: Float get() = timings.holdMediumMs.toFloat() / timings.holdLongMs

    private fun emitTaps(t: Long) {
        val g = when (taps) {
            1 -> Gesture.LONG_PRESS
            2 -> Gesture.DOUBLE_LONG_PRESS
            else -> Gesture.TRIPLE_LONG_PRESS
        }
        resetSequence()
        onGesture(g, t)
    }

    private fun cancel(t: Long) {
        resetSequence()
        onFeedback(GestureFeedback.CANCELLED, t)
    }

    private fun resetSequence() {
        taps = 0
        gapDeadline = Long.MAX_VALUE
        sawChange = false
        mediumSignalled = false
        longFired = false
    }
}
