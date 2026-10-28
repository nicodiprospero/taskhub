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
 */
class VisualizerSource : AudioSource {

    override val id = SourceId.VISUALIZER

    private val _frames = MutableSharedFlow<RawAudio>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames: Flow<RawAudio> = _frames.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val errors: Flow<String> = _errors.asSharedFlow()

    private var thread: HandlerThread? = null
    private var visualizer: Visualizer? = null
    private var pool: RawAudioPool? = null
    private var sampleRate = 0

    override fun start() {
        if (thread != null) return
        val t = HandlerThread("GlyphWave-visualizer", Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
        thread = t
        // Visualizer delivers callbacks on the looper of the thread that created it.
        Handler(t.looper).post {
            try {
                val v = Visualizer(0)
                val size = Visualizer.getCaptureSizeRange()[1]
                v.captureSize = size
                v.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
                sampleRate = v.samplingRate / 1000 // mHz → Hz
                pool = RawAudioPool(8, size)
                val rate = Visualizer.getMaxCaptureRate()
                v.setDataCaptureListener(listener, rate, true, false)
                v.enabled = true
                visualizer = v
                Log.i(TAG, "AUDIO visualizer started size=$size rate=${rate / 1000.0}Hz sampleRate=$sampleRate")
            } catch (e: Exception) {
                // Typically RuntimeException "Cannot initialize Visualizer engine" or UnsupportedOperation.
                Log.e(TAG, "AUDIO visualizer failed", e)
                _errors.tryEmit("Visualizer: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
            val wf = waveform ?: return
            val p = pool ?: return
            val block = p.obtain()
            val n = minOf(wf.size, block.samples.size)
            // Unsigned 8-bit PCM centred on 128.
            for (i in 0 until n) block.samples[i] = ((wf[i].toInt() and 0xFF) - 128) / 128f
            block.count = n
            block.sampleRate = samplingRate / 1000
            block.timestampNanos = SystemClock.elapsedRealtimeNanos()
            _frames.tryEmit(block)
        }

        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) = Unit
    }

    override fun stop() {
        val t = thread ?: return
        thread = null
        Handler(t.looper).post {
            visualizer?.let {
                runCatching { it.enabled = false }
                it.release()
            }
            visualizer = null
            Log.i(TAG, "AUDIO visualizer released")
            t.quitSafely()
        }
    }
}
