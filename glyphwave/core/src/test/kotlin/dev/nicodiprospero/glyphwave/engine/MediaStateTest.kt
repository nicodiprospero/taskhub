package dev.nicodiprospero.glyphwave.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStateTest {

    private val playing = MediaState(
        access = MediaState.Access.OK,
        playback = MediaState.Playback.PLAYING,
        positionMs = 10_000,
        positionUpdatedAtMs = 1_000,
        durationMs = 200_000,
    )

    @Test
    fun `extrapolates position while playing`() {
        assertEquals(12_500, playing.positionAt(3_500))
    }

    @Test
    fun `respects playback speed`() {
        assertEquals(15_000, playing.copy(playbackSpeed = 2f).positionAt(3_500))
    }

    @Test
    fun `does not move while paused`() {
        assertEquals(10_000, playing.copy(playback = MediaState.Playback.PAUSED).positionAt(99_000))
    }

    @Test
    fun `clamps to duration`() {
        assertEquals(200_000, playing.positionAt(10_000_000))
    }
}
