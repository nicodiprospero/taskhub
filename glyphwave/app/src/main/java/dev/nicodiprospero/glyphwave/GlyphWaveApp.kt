package dev.nicodiprospero.glyphwave

import android.app.Application
import dev.nicodiprospero.glyphwave.engine.Engine
import dev.nicodiprospero.glyphwave.glyph.OutputStage
import dev.nicodiprospero.glyphwave.media.MediaBridge
import dev.nicodiprospero.glyphwave.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GlyphWaveApp : Application() {

    lateinit var settings: SettingsRepository
        private set

    /** Lives as long as the process. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        MediaBridge.start(this)
        appScope.launch { settings.mediaSourcePolicy.collect { MediaBridge.policy = it } }

        // Display settings → the shared renderer (also picked up by the toy on its next frame).
        appScope.launch { settings.visualizerMode.collect { Engine.renderer().selectMode(it) } }
        appScope.launch { settings.bandFocus.collect { Engine.renderer().focus = it } }
        appScope.launch {
            combine(settings.gamma, settings.brightness) { g, b -> g to b }.collect { (g, b) ->
                Engine.renderer().output = OutputStage(g, b)
            }
        }
        appScope.launch { settings.fps.collect { Engine.fps = it } }
    }
}
