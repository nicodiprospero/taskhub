package dev.nicodiprospero.glyphwave.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.nicodiprospero.glyphwave.audio.SourceId
import dev.nicodiprospero.glyphwave.media.MediaBridge
import dev.nicodiprospero.glyphwave.modes.BandFocus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persistent user settings. Grows phase by phase; every setting has a safe default. */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val mediaSourcePolicy: Flow<MediaBridge.SourcePolicy> = store.data.map { prefs ->
        prefs[KEY_MEDIA_SOURCE]
            ?.let { runCatching { MediaBridge.SourcePolicy.valueOf(it) }.getOrNull() }
            ?: MediaBridge.SourcePolicy.YT_MUSIC_ONLY
    }

    suspend fun setMediaSourcePolicy(policy: MediaBridge.SourcePolicy) {
        store.edit { it[KEY_MEDIA_SOURCE] = policy.name }
    }

    val audioSource: Flow<SourceId> = store.data.map { prefs ->
        prefs[KEY_AUDIO_SOURCE]?.let { runCatching { SourceId.valueOf(it) }.getOrNull() } ?: DEFAULT_AUDIO_SOURCE
    }

    suspend fun setAudioSource(id: SourceId) {
        store.edit { it[KEY_AUDIO_SOURCE] = id.name }
    }

    /** Source B only: restrict playback capture to YouTube Music's uid. */
    val captureOnlyYtMusic: Flow<Boolean> = store.data.map { it[KEY_CAPTURE_ONLY_YT] ?: false }

    suspend fun setCaptureOnlyYtMusic(value: Boolean) {
        store.edit { it[KEY_CAPTURE_ONLY_YT] = value }
    }

    // ---- Visualizer / display (Phase 3) ----

    val visualizerMode: Flow<String> = store.data.map { it[KEY_MODE] ?: DEFAULT_MODE }
    suspend fun setVisualizerMode(id: String) = store.edit { it[KEY_MODE] = id }

    val bandFocus: Flow<BandFocus> = store.data.map { p ->
        p[KEY_FOCUS]?.let { runCatching { BandFocus.valueOf(it) }.getOrNull() } ?: BandFocus.FULL
    }
    suspend fun setBandFocus(f: BandFocus) = store.edit { it[KEY_FOCUS] = f.name }

    /** LED gamma (perceived → drive). 2.2 per D-004's "top end compresses" observation. */
    val gamma: Flow<Float> = store.data.map { it[KEY_GAMMA] ?: DEFAULT_GAMMA }
    suspend fun setGamma(v: Float) = store.edit { it[KEY_GAMMA] = v.coerceIn(1f, 3f) }

    /** Global brightness cap 0.05..1. */
    val brightness: Flow<Float> = store.data.map { it[KEY_BRIGHTNESS] ?: DEFAULT_BRIGHTNESS }
    suspend fun setBrightness(v: Float) = store.edit { it[KEY_BRIGHTNESS] = v.coerceIn(0.05f, 1f) }

    val fps: Flow<Int> = store.data.map { it[KEY_FPS] ?: 30 }
    suspend fun setFps(v: Int) = store.edit { it[KEY_FPS] = if (v >= 60) 60 else 30 }

    companion object {
        /** Phase 2 report (DECISIONS.md D-023): Visualizer works on the owner's phone, screen off too, no consent. */
        val DEFAULT_AUDIO_SOURCE = SourceId.VISUALIZER
        const val DEFAULT_MODE = "radial"
        const val DEFAULT_GAMMA = 2.2f
        const val DEFAULT_BRIGHTNESS = 0.8f

        private val KEY_MODE = stringPreferencesKey("visualizer_mode")
        private val KEY_FOCUS = stringPreferencesKey("band_focus")
        private val KEY_GAMMA = floatPreferencesKey("gamma")
        private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        private val KEY_FPS = intPreferencesKey("fps")

        private val KEY_MEDIA_SOURCE = stringPreferencesKey("media_source_policy")
        private val KEY_AUDIO_SOURCE = stringPreferencesKey("audio_source")
        private val KEY_CAPTURE_ONLY_YT = booleanPreferencesKey("capture_only_yt_music")
    }
}
