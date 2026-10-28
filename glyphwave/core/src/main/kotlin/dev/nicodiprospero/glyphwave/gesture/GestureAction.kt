package dev.nicodiprospero.glyphwave.gesture

/** What a gesture does. Executed by ActionExecutor (app); mapping is user-configurable. */
enum class GestureAction(val label: String) {
    NONE("Nothing"),
    NEXT_MODE("Next visualizer mode"),
    NEXT_TRACK("Next track"),
    PREVIOUS_TRACK("Previous track"),
    PLAY_PAUSE("Play / pause"),
    NOW_PLAYING("Show now playing"),
    LIKE("Like song"),
    SEEK_FORWARD("Seek +15 s"),
    CYCLE_BRIGHTNESS("Cycle brightness"),
    LOCK_MODE("Lock current mode"),
    TOGGLE_BEAT_FLASH("Toggle beat flash"),
    ;

    companion object {
        /** Brief's default mapping. */
        val DEFAULTS: Map<Gesture, GestureAction> = mapOf(
            Gesture.LONG_PRESS to NEXT_MODE,
            Gesture.DOUBLE_LONG_PRESS to NEXT_TRACK,
            Gesture.TRIPLE_LONG_PRESS to PREVIOUS_TRACK,
            Gesture.HOLD_MEDIUM to PLAY_PAUSE,
            Gesture.HOLD_LONG to NOW_PLAYING,
        )
    }
}
