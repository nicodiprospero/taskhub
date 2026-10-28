package dev.nicodiprospero.glyphwave.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import dev.nicodiprospero.glyphwave.GlyphWaveApp
import dev.nicodiprospero.glyphwave.TAG
import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.media.MediaBridge
import dev.nicodiprospero.glyphwave.debug.GlyphEvent
import dev.nicodiprospero.glyphwave.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The GlyphWave Glyph Toy.
 *
 * Phase 0: shows diagnostic [TestPattern]s and logs every raw Glyph event with a monotonic
 * timestamp (logcat + in-app debug list) so the gesture recognizer can be built on observed data.
 * A long press ("change") advances to the next test pattern.
 */
class GlyphWaveToyService : Service() {

    // Glyph events are handled on a dedicated high-priority thread, not the main looper, so the
    // timestamps we log are not skewed by UI work in the companion app (same process).
    private lateinit var eventThread: HandlerThread
    private lateinit var messenger: Messenger

    private var gmm: GlyphMatrixManager? = null
    private var scope: CoroutineScope? = null
    private var mask: CircleMask? = null
    private var canvas: MatrixCanvas? = null

    @Volatile private var connected = false

    override fun onCreate() {
        super.onCreate()
        eventThread = HandlerThread("GlyphWave-events", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
        messenger = Messenger(EventHandler(eventThread))
        lifecycle("service onCreate")
    }

    override fun onBind(intent: Intent?): IBinder {
        lifecycle("onBind action=${intent?.action}")
        Engine.updateToyStatus { it.copy(state = Engine.ToyState.BINDING, lastError = null) }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        gmm = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        lifecycle("onUnbind")
        shutdown()
        return false
    }

    override fun onDestroy() {
        lifecycle("service onDestroy")
        shutdown()
        eventThread.quitSafely()
        super.onDestroy()
    }

    private fun shutdown() {
        connected = false
        scope?.cancel()
        scope = null
        gmm?.let {
            runCatching { it.turnOff() }.onFailure { e -> Log.w(TAG, "turnOff failed", e) }
            runCatching { it.unInit() }.onFailure { e -> Log.w(TAG, "unInit failed", e) }
        }
        gmm = null
        Engine.updateToyStatus { it.copy(state = Engine.ToyState.UNBOUND, registered = false) }
    }

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            val manager = gmm ?: return // unbound before the system service answered
            val registered = runCatching { manager.register(Glyph.DEVICE_23112) }
                .onFailure { Log.e(TAG, "register failed", it) }
                .getOrDefault(false)
            val reported = runCatching { Common.getDeviceMatrixLength() }.getOrNull()
            val length = MatrixSpec.resolveLength(reported)
            lifecycle("glyph service connected register=$registered matrixLength=$reported→$length")

            mask = CircleMask(length)
            canvas = MatrixCanvas(length)
            connected = true
            Engine.updateToyStatus {
                it.copy(state = Engine.ToyState.CONNECTED, matrixLength = length, registered = registered)
            }
            scope?.launch {
                Engine.testPattern.collectLatest { pattern ->
                    when {
                        pattern == null -> visualizerLoop(length)
                        pattern.animated -> patternLoop(pattern)
                        else -> show(pattern)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            lifecycle("glyph service disconnected")
            connected = false
            Engine.updateToyStatus { it.copy(state = Engine.ToyState.DISCONNECTED) }
        }
    }

    /**
     * The visualizer: shared [MatrixRenderer] (same one the app preview uses) at the configured fps.
     * Main thread; allocation-free per frame (frame + output arrays are allocated once here).
     */
    private suspend fun visualizerLoop(length: Int) {
        val manager = gmm ?: return
        val renderer = Engine.renderer(length)
        val audio = AudioFrame()
        val out = IntArray(length * length)
        var slowFrames = 0
        Log.i(TAG, "render loop start mode=${renderer.currentMode.id} focus=${renderer.focus} fps=${Engine.fps}")
        while (currentCoroutineContext().isActive && connected) {
            val start = SystemClock.elapsedRealtimeNanos()
            Engine.audioFrames.readInto(audio)
            renderer.render(audio, MediaBridge.state.value, start / 1_000_000, out)
            try {
                manager.setMatrixFrame(out)
            } catch (e: GlyphException) {
                Log.e(TAG, "setMatrixFrame failed", e)
                Engine.updateToyStatus { it.copy(lastError = "setMatrixFrame: ${e.message}") }
                return
            }
            Engine.matrixFrame.publish(out, start / 1_000_000)
            val tookNs = SystemClock.elapsedRealtimeNanos() - start
            if (tookNs > SLOW_FRAME_NS && ++slowFrames % 30 == 1) {
                Log.w(TAG, "slow frame ${tookNs / 1_000}us (${slowFrames} so far)")
            }
            val frameNs = 1_000_000_000L / Engine.fps
            delay(((frameNs - tookNs) / 1_000_000).coerceAtLeast(1))
        }
    }

    /** Animated diagnostic pattern (AUDIO_LEVEL) at 30 fps. */
    private suspend fun patternLoop(pattern: TestPattern) {
        while (currentCoroutineContext().isActive && connected) {
            show(pattern, Engine.audioLevel, logFrame = false)
            delay(33)
        }
    }

    /** Main thread only. */
    private fun show(pattern: TestPattern, level: Float = 0f, logFrame: Boolean = true) {
        val manager = gmm ?: return
        val c = canvas ?: return
        val m = mask ?: return
        if (!connected) return
        TestPatterns.render(pattern, c, m, level)
        try {
            manager.setMatrixFrame(c.pixels)
            Engine.matrixFrame.publish(c.pixels, SystemClock.elapsedRealtime())
            if (logFrame) Log.i(TAG, "frame pattern=${pattern.name} max=${c.pixels.max()} lit=${c.pixels.count { it > 0 }}")
        } catch (e: GlyphException) {
            Log.e(TAG, "setMatrixFrame failed", e)
            Engine.updateToyStatus { it.copy(lastError = "setMatrixFrame: ${e.message}") }
        }
    }

    private fun onLongPress() {
        // Phase 3 behaviour (= the brief's default "long press → next mode"); the GestureRecognizer takes over in Phase 4.
        val pattern = Engine.testPattern.value
        if (pattern != null) {
            Engine.testPattern.value = pattern.next()
            return
        }
        val mode = Engine.renderer(canvas?.length ?: MatrixSpec.DEFAULT_LENGTH).nextMode()
        Log.i(TAG, "mode → ${mode.id}")
        val app = application as GlyphWaveApp
        app.appScope.launch { app.settings.setVisualizerMode(mode.id) }
    }

    private inner class EventHandler(thread: HandlerThread) : Handler(thread.looper) {
        override fun handleMessage(msg: Message) {
            val now = SystemClock.elapsedRealtimeNanos()
            // msg.when = uptime at enqueue → how long the event waited in our queue.
            val queueLagMs = SystemClock.uptimeMillis() - msg.`when`
            val data = msg.data
            val value = data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)

            val (kind, name) = when {
                msg.what == GlyphToy.MSG_GLYPH_TOY && value in KNOWN_EVENTS -> GlyphEvent.Kind.BUTTON to value!!
                else -> GlyphEvent.Kind.UNKNOWN to "what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2} ${describe(data)}"
            }
            val event = Engine.glyphEvents.record(now, kind, name)
            Log.i(TAG, "EVT ${event.format()} queueLag=${queueLagMs}ms thread=${Thread.currentThread().name}")

            if (msg.what == GlyphToy.MSG_GLYPH_TOY && value == GlyphToy.EVENT_CHANGE) {
                scope?.launch { onLongPress() }
            }
        }
    }

    private fun lifecycle(what: String) {
        val event = Engine.glyphEvents.record(SystemClock.elapsedRealtimeNanos(), GlyphEvent.Kind.LIFECYCLE, what)
        Log.i(TAG, "EVT ${event.format()}")
    }

    private companion object {
        const val SLOW_FRAME_NS = 8_000_000L

        val KNOWN_EVENTS = setOf(
            GlyphToy.EVENT_CHANGE,
            GlyphToy.EVENT_ACTION_DOWN,
            GlyphToy.EVENT_ACTION_UP,
            GlyphToy.EVENT_AOD,
        )

        /** Dumps every key so undocumented payloads show up during instrumentation. */
        @Suppress("DEPRECATION")
        fun describe(bundle: Bundle?): String =
            bundle?.keySet()?.joinToString(prefix = "{", postfix = "}") { "$it=${bundle.get(it)}" } ?: "{}"
    }
}
