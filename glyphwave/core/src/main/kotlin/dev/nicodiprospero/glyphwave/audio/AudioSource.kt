package dev.nicodiprospero.glyphwave.audio

import kotlinx.coroutines.flow.Flow

/** Where audio comes from. Order = preference when nothing is configured (see DECISIONS.md, Phase 2). */
enum class SourceId(val label: String) {
    /** A) android.media.audiofx.Visualizer on the global output mix (session 0). */
    VISUALIZER("Visualizer (session 0)"),

    /** B) MediaProjection + AudioPlaybackCaptureConfiguration → AudioRecord. */
    PLAYBACK_CAPTURE("Playback capture"),
}

interface AudioSource {
    val id: SourceId

    /** Starts capturing; throws or reports via [errors] if the platform refuses. */
    fun start()

    /** Stops and releases every native resource. Idempotent. */
    fun stop()

    /** Captured blocks. Hot; emits only while started. Blocks are pooled (see [RawAudio]). */
    val frames: Flow<RawAudio>

    /** Fatal problems after start (e.g. projection revoked). */
    val errors: Flow<String>
}
