package dev.nicodiprospero.glyphwave.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import dev.nicodiprospero.glyphwave.R
import dev.nicodiprospero.glyphwave.TAG
import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.AudioStatus
import dev.nicodiprospero.glyphwave.engine.Engine
import dev.nicodiprospero.glyphwave.media.MediaBridge
import dev.nicodiprospero.glyphwave.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns audio capture. Started ONLY from the foreground (app button, later the
 * QS tile) because RECORD_AUDIO is a while-in-use permission. The Glyph Toy never starts it.
 */
class AudioEngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var source: AudioSource? = null
    private val meter = LevelMeter()
    private val analyzer = AudioAnalyzer()
    private val frame = AudioFrame()
    @Volatile private var lastPublishNanos = 0L
    @Volatile private var lastStatsNanos = 0L
    private var warnedSilent = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "AUDIO stop requested")
                stopSelf()
            }
            ACTION_START -> start(intent)
            else -> stopSelf() // restarted without a request (we never ask for sticky restarts)
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val id = runCatching { SourceId.valueOf(intent.getStringExtra(EXTRA_SOURCE)!!) }.getOrNull()
        if (id == null) {
            fail("No audio source given")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("RECORD_AUDIO not granted")
            return
        }
        stopSource()

        val type = when (id) {
            SourceId.VISUALIZER -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            SourceId.PLAYBACK_CAPTURE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            // Must precede getMediaProjection() for source B.
            startForeground(NOTIFICATION_ID, buildNotification(id), type)
        } catch (e: Exception) {
            Log.e(TAG, "AUDIO startForeground failed", e)
            fail("Cannot start foreground service: ${e.message}")
            return
        }

        val src = try {
            when (id) {
                SourceId.VISUALIZER -> VisualizerSource()
                SourceId.PLAYBACK_CAPTURE -> {
                    val consent = intent.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
                        ?: throw IllegalStateException("Missing screen-capture consent")
                    val uid = intent.getIntExtra(EXTRA_MATCH_UID, -1).takeIf { it >= 0 }
                    PlaybackCaptureSource(applicationContext, intent.getIntExtra(EXTRA_RESULT_CODE, 0), consent, uid)
                }
            }
        } catch (e: Exception) {
            fail("Source setup failed: ${e.message}")
            return
        }

        meter.reset()
        analyzer.reset()
        warnedSilent = false
        source = src
        Engine.publishAudio(AudioStatus(running = true, source = id))
        scope.launch { src.frames.collect { onBlock(id, it) } }
        scope.launch {
            src.errors.collect { msg ->
                Log.e(TAG, "AUDIO source error: $msg")
                // Fatal for any source: stop the engine so the UI never shows RUNNING without audio.
                withContext(Dispatchers.Main) { fail(msg) }
            }
        }
        try {
            src.start()
        } catch (e: Exception) {
            Log.e(TAG, "AUDIO source start failed", e)
            fail("${id.label}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Capture thread → Default dispatcher. Allocation-free except the throttled status snapshot. */
    private fun onBlock(id: SourceId, block: RawAudio) {
        meter.process(block.samples, block.count, block.timestampNanos)
        Engine.audioLevel = meter.level
        val frameStart = System.nanoTime()
        analyzer.process(block.samples, block.count, block.sampleRate, block.timestampNanos, frame)
        Engine.audioFrames.publish(frame)
        val analysisUs = (System.nanoTime() - frameStart) / 1_000
        if (analysisUs > SLOW_ANALYSIS_US) Log.w(TAG, "AUDIO slow analysis ${analysisUs}us")

        val now = block.timestampNanos
        if (now - lastPublishNanos < PUBLISH_INTERVAL_NANOS) return
        lastPublishNanos = now

        val playing = MediaBridge.state.value.isPlaying
        val silentWhilePlaying = playing && meter.silentForMs > SILENCE_ALARM_MS
        if (silentWhilePlaying && !warnedSilent) {
            warnedSilent = true
            Log.w(TAG, "AUDIO ${id.name} delivers SILENCE for ${meter.silentForMs} ms while media is PLAYING")
        } else if (!silentWhilePlaying && warnedSilent && meter.silentForMs == 0L) {
            warnedSilent = false
            Log.i(TAG, "AUDIO ${id.name} signal back")
        }
        Engine.publishAudio(
            AudioStatus(
                running = true,
                source = id,
                sampleRate = block.sampleRate,
                blockSize = block.count,
                blockRate = meter.blockRate,
                rms = meter.rms,
                peak = meter.peak,
                level = meter.level,
                silentForMs = meter.silentForMs,
                silentWhilePlaying = silentWhilePlaying,
                error = Engine.audio.value.error,
            ),
        )
        if (now - lastStatsNanos >= STATS_INTERVAL_NANOS) {
            lastStatsNanos = now
            Log.i(
                TAG,
                "AUDIO stats src=${id.name} rate=%.1f/s block=%d sr=%d rms=%.4f peak=%.3f level=%.2f silent=%dms playing=%s"
                    .format(meter.blockRate, block.count, block.sampleRate, meter.rms, meter.peak, meter.level, meter.silentForMs, playing),
            )
        }
    }

    private fun fail(message: String) {
        Log.e(TAG, "AUDIO $message")
        stopSource()
        Engine.publishAudio(AudioStatus(running = false, error = message))
        stopSelf()
    }

    private fun stopSource() {
        source?.stop()
        source = null
        Engine.audioLevel = 0f
        Engine.audioFrames.clear()
    }

    override fun onDestroy() {
        stopSource()
        scope.cancel()
        val error = Engine.audio.value.error
        Engine.publishAudio(AudioStatus(running = false, error = error))
        Log.i(TAG, "AUDIO engine stopped")
        super.onDestroy()
    }

    private fun buildNotification(id: SourceId): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Audio engine", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while GlyphWave listens to playback audio"
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AudioEngineService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_engine)
            .setContentTitle("GlyphWave is listening")
            .setContentText(id.label)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val ACTION_START = "dev.nicodiprospero.glyphwave.audio.START"
        private const val ACTION_STOP = "dev.nicodiprospero.glyphwave.audio.STOP"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_CONSENT = "consent"
        private const val EXTRA_MATCH_UID = "matchUid"
        private const val CHANNEL_ID = "engine"
        private const val NOTIFICATION_ID = 1
        private const val PUBLISH_INTERVAL_NANOS = 100_000_000L // 10 Hz UI snapshots
        private const val STATS_INTERVAL_NANOS = 5_000_000_000L
        private const val SILENCE_ALARM_MS = 2_000L
        private const val SLOW_ANALYSIS_US = 4_000L

        fun startVisualizer(context: Context) {
            context.startForegroundService(
                Intent(context, AudioEngineService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SOURCE, SourceId.VISUALIZER.name),
            )
        }

        fun startPlaybackCapture(context: Context, resultCode: Int, consent: Intent, matchUid: Int?) {
            context.startForegroundService(
                Intent(context, AudioEngineService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SOURCE, SourceId.PLAYBACK_CAPTURE.name)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_CONSENT, consent)
                    .putExtra(EXTRA_MATCH_UID, matchUid ?: -1),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AudioEngineService::class.java).setAction(ACTION_STOP))
        }
    }
}
