package dev.nicodiprospero.glyphwave.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dev.nicodiprospero.glyphwave.TAG
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Source B: MediaProjection + [AudioPlaybackCaptureConfiguration] → [AudioRecord], PCM16 stereo
 * 48 kHz, down-mixed to mono. Requires the user's screen-capture consent (result code + data from
 * the consent Activity) and must run inside a foreground service of type mediaProjection that has
 * ALREADY called startForeground().
 *
 * Apps can opt out of playback capture (allowAudioPlaybackCapture=false); then we read silence,
 * which the engine reports as "silent while playing".
 */
class PlaybackCaptureSource(
    private val context: Context,
    private val resultCode: Int,
    private val consent: Intent,
    /** If set, capture only this app's audio (e.g. YT Music's uid). */
    private val matchUid: Int? = null,
) : AudioSource {

    override val id = SourceId.PLAYBACK_CAPTURE

    private val _frames = MutableSharedFlow<RawAudio>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames: Flow<RawAudio> = _frames.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val errors: Flow<String> = _errors.asSharedFlow()

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    @Volatile private var running = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "AUDIO projection stopped by system/user")
            _errors.tryEmit("Screen-capture permission ended")
            stop()
        }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the engine before starting.
    override fun start() {
        if (running) return
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(resultCode, consent)
            ?: throw IllegalStateException("getMediaProjection returned null")
        // Required before capturing on Android 14+.
        p.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = p

        val config = AudioPlaybackCaptureConfiguration.Builder(p)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .apply { matchUid?.let { addMatchingUid(it) } }
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf, BLOCK_FRAMES * 2 * 2 * 4))
            .setAudioPlaybackCaptureConfig(config)
            .build()
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialised" }
        record = r
        r.startRecording()
        running = true
        Log.i(TAG, "AUDIO playback capture started rate=$SAMPLE_RATE block=$BLOCK_FRAMES minBuf=$minBuf uid=$matchUid")

        reader = Thread({ readLoop(r) }, "GlyphWave-capture").apply { start() }
    }

    private fun readLoop(r: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val pool = RawAudioPool(8, BLOCK_FRAMES)
        val pcm = ShortArray(BLOCK_FRAMES * 2)
        while (running) {
            val read = r.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
            if (read < 0) {
                Log.e(TAG, "AUDIO AudioRecord.read error $read")
                _errors.tryEmit("AudioRecord error $read")
                break
            }
            val frames = read / 2
            val block = pool.obtain()
            for (i in 0 until frames) {
                block.samples[i] = (pcm[2 * i] + pcm[2 * i + 1]) / 65536f
            }
            block.count = frames
            block.sampleRate = SAMPLE_RATE
            block.timestampNanos = SystemClock.elapsedRealtimeNanos()
            _frames.tryEmit(block)
        }
    }

    override fun stop() {
        if (!running && projection == null) return
        running = false
        record?.let { runCatching { it.stop() } }
        reader?.join(500)
        reader = null
        record?.release()
        record = null
        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            it.stop()
        }
        projection = null
        Log.i(TAG, "AUDIO playback capture released")
    }

    companion object {
        const val SAMPLE_RATE = 48_000

        /** 800 frames = 16.7 ms → 60 blocks/s. */
        const val BLOCK_FRAMES = 800
    }
}
