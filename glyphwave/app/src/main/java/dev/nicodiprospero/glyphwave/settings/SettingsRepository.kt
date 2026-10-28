package dev.nicodiprospero.glyphwave.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    private companion object {
        val KEY_MEDIA_SOURCE = stringPreferencesKey("media_source_policy")
    }
}
