package dev.nicodiprospero.glyphwave.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.nicodiprospero.glyphwave.audio.SourceId
import dev.nicodiprospero.glyphwave.media.MediaBridge
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

    companion object {
        /** Provisional until the Phase 2 on-device report (DECISIONS.md) picks the default. */
        val DEFAULT_AUDIO_SOURCE = SourceId.VISUALIZER

        private val KEY_MEDIA_SOURCE = stringPreferencesKey("media_source_policy")
        private val KEY_AUDIO_SOURCE = stringPreferencesKey("audio_source")
        private val KEY_CAPTURE_ONLY_YT = booleanPreferencesKey("capture_only_yt_music")
    }
}
