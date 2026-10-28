package dev.nicodiprospero.glyphwave.audio

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dev.nicodiprospero.glyphwave.TAG
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Source A: [Visualizer] attached to audio session 0 (the global output mix).
 * Needs RECORD_AUDIO + MODIFY_AUDIO_SETTINGS. Session 0 is deprecated; on some builds it only ever
 * returns silence — the engine detects that ("silent while playing") and reports it.
 *
 * Only the 8-bit waveform is used: both sources then share one FFT/DSP path (Phase 3).
 *
 * POLLED at [POLL_HZ] with getWaveForm() instead of the ≤ 20 Hz capture listener: each poll returns the
 * most recent captureSize samples, so we get overlapping windows 3× as often and no kick falls between
 * two snapshots (DECISIONS.md D-028). Falls back to the listener if polling isn't supported.
 */
class VisualizerSource : AudioSource {

    private companion object {
        const val POLL_HZ = 60
        const val POLL_MS = 1000L / POLL_HZ
        const val MAX_ATTEMPTS = 5
        const val RETRY_MS = 400L
    }

    override val id = SourceId.VISUALIZER

    private val _frames = MutableSharedFlow<RawAudio>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames: Flow<RawAudio> = _frames.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val errors: Flow<String> = _errors.asSharedFlow()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var waveform: ByteArray? = null
    private var polling = false
    private var visualizer: Visualizer? = null
    private var pool: RawAudioPool? = null
    private var sampleRate = 0

    override fun start() {
        if (thread != null) return
        val t = HandlerThread("GlyphWave-visualizer", Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
        thread = t
        // Visualizer delivers callbacks on the looper of the thread that created it.
        val h = Handler(t.looper)
        handler = h
        h.post { attempt(1) }
    }

    /**
     * Creates the Visualizer, retrying with backoff: error -3 (NO_INIT) happens when a previous session-0
     * Visualizer (ours from a quick restart, or another app's) still holds the effect.
     */
    private fun attempt(n: Int) {
        if (thread == null) return // stopped while waiting
        try {
            val v = Visualizer(0)
            val size = Visualizer.getCaptureSizeRange()[1]
            v.captureSize = size
            v.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            sampleRate = v.samplingRate / 1000 // mHz → Hz
            pool = RawAudioPool(8, size)
            v.enabled = true
            visualizer = v
            val buf = ByteArray(size)
            waveform = buf
            if (v.getWaveForm(buf) == Visualizer.SUCCESS) {
                polling = true
                handler?.post(poll)
                Log.i(TAG, "AUDIO visualizer started (polling ${POLL_HZ}Hz) size=$size sampleRate=$sampleRate attempt=$n")
            } else {
                v.enabled = false
                val rate = Visualizer.getMaxCaptureRate()
                v.setDataCaptureListener(listener, rate, true, false)
                v.enabled = true
                Log.i(TAG, "AUDIO visualizer started (listener ${rate / 1000.0}Hz) size=$size sampleRate=$sampleRate attempt=$n")
            }
        } catch (e: Exception) {
            // Typically RuntimeException "Cannot initialize Visualizer engine, error: -3".
            visualizer?.let { runCatching { it.release() } }
            visualizer = null
            if (n < MAX_ATTEMPTS) {
                Log.w(TAG, "AUDIO visualizer attempt $n failed (${e.message}); retrying in ${RETRY_MS * n} ms")
                handler?.postDelayed({ attempt(n + 1) }, RETRY_MS * n)
            } else {
                Log.e(TAG, "AUDIO visualizer failed after $n attempts", e)
                _errors.tryEmit(
                    "Visualizer unavailable (${e.message}). Another app may be using the global audio " +
                        "visualizer — e.g. Glyph Interface › Music visualization. Turn it off, or use source B.",
                )
            }
        }
    }

    /** Fixed-rate poll on the visualizer thread (drift-free: schedules against uptime). */
    private val poll = object : Runnable {
        private var next = 0L
        override fun run() {
            val v = visualizer ?: return
            val buf = waveform ?: return
            if (!polling) return
            val now = SystemClock.uptimeMillis()
            if (v.getWaveForm(buf) == Visualizer.SUCCESS) emit(buf, sampleRate * 1000)
            next = if (next == 0L || next < now - POLL_MS) now + POLL_MS else next + POLL_MS
            handler?.postAtTime(this, next)
        }
    }

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
            emit(waveform ?: return, samplingRate)
        }

        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) = Unit
    }

    private fun emit(wf: ByteArray, samplingRateMilliHz: Int) {
        val p = pool ?: return
        val block = p.obtain()
        val n = minOf(wf.size, block.samples.size)
        // Unsigned 8-bit PCM centred on 128.
        for (k in 0 until n) block.samples[k] = ((wf[k].toInt() and 0xFF) - 128) / 128f
        block.count = n
        block.sampleRate = samplingRateMilliHz / 1000
        block.timestampNanos = SystemClock.elapsedRealtimeNanos()
        _frames.tryEmit(block)
    }

    /** Releases synchronously (waits ≤ 1 s) so an immediate restart doesn't hit error -3. */
    override fun stop() {
        val t = thread ?: return
        thread = null
        polling = false
        val h = handler ?: Handler(t.looper)
        handler = null
        h.removeCallbacksAndMessages(null)
        val released = java.util.concurrent.CountDownLatch(1)
        h.post {
            visualizer?.let {
                runCatching { it.enabled = false }
                it.release()
            }
            visualizer = null
            Log.i(TAG, "AUDIO visualizer released")
            released.countDown()
            t.quitSafely()
        }
        if (Thread.currentThread() !== t && !released.await(1, java.util.concurrent.TimeUnit.SECONDS)) {
            Log.w(TAG, "AUDIO visualizer release timed out")
        }
    }
}
