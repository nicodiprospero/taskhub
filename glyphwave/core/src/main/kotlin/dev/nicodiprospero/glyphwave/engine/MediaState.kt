package dev.nicodiprospero.glyphwave.engine

/** Package name of YouTube Music, the primary media source. */
const val YT_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

/**
 * Android-free snapshot of the tracked media session. Produced by MediaBridge (app), consumed by
 * modes and UI. Album art is pre-converted to a small luminance grid so modes never touch Bitmap.
 */
data class MediaState(
    val access: Access = Access.NO_NOTIFICATION_ACCESS,
    /** Package of the tracked session, or null if none. */
    val packageName: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val playback: Playback = Playback.NONE,
    /** Position at [positionUpdatedAtMs] (elapsedRealtime clock), in ms. */
    val positionMs: Long = 0,
    val positionUpdatedAtMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val durationMs: Long = 0,
    val customActions: List<CustomAction> = emptyList(),
    /** Changes whenever the track changes; modes use it to regenerate per-track data. */
    val trackKey: String? = null,
    val art: ArtPixels? = null,
) {
    enum class Access {
        /** User hasn't granted notification access, so active sessions can't be listed. */
        NO_NOTIFICATION_ACCESS,

        /** YouTube Music is not installed (only relevant for the YT-Music-only policy). */
        YT_MUSIC_NOT_INSTALLED,

        /** Access OK, but no matching session right now (app closed, nothing played yet). */
        NO_SESSION,

        /** Tracking a session. */
        OK,
    }

    enum class Playback { NONE, STOPPED, PAUSED, PLAYING, BUFFERING, ERROR, OTHER }

    data class CustomAction(val action: String, val name: String)

    val isPlaying: Boolean get() = playback == Playback.PLAYING

    /** Extrapolated playback position at [nowMs] (elapsedRealtime), clamped to the duration. */
    fun positionAt(nowMs: Long): Long {
        val base = if (isPlaying && positionUpdatedAtMs > 0) {
            positionMs + ((nowMs - positionUpdatedAtMs) * playbackSpeed).toLong()
        } else {
            positionMs
        }
        return if (durationMs > 0) base.coerceIn(0, durationMs) else base.coerceAtLeast(0)
    }
}

/** Square luminance grid (0..255), row-major. Centre-cropped from the album art. */
class ArtPixels(val size: Int, val luma: IntArray) {
    init {
        require(luma.size == size * size) { "luma must be size×size" }
    }
}
