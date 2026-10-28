package dev.nicodiprospero.glyphwave

import android.app.Application
import dev.nicodiprospero.glyphwave.media.MediaBridge
import dev.nicodiprospero.glyphwave.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    }
}
