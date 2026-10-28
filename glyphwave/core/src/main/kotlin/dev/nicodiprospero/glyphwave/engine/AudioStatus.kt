package dev.nicodiprospero.glyphwave.engine

import dev.nicodiprospero.glyphwave.audio.SourceId

/** UI-facing snapshot of the audio engine, published at ~10 Hz. */
data class AudioStatus(
    val running: Boolean = false,
    val source: SourceId? = null,
    val sampleRate: Int = 0,
    val blockSize: Int = 0,
    val blockRate: Float = 0f,
    val rms: Float = 0f,
    val peak: Float = 0f,
    val level: Float = 0f,
    val silentForMs: Long = 0,
    /** True when the source delivers digital silence > 2 s while the media session is PLAYING. */
    val silentWhilePlaying: Boolean = false,
    val error: String? = null,
)
