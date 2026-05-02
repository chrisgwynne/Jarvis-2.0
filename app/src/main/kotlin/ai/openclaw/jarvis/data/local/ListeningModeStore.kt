package ai.openclaw.jarvis.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.voice.ListeningMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.listeningModeDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "jarvis_listening_mode")

@Singleton
class ListeningModeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.listeningModeDataStore
    private val KEY   = stringPreferencesKey("mode")

    suspend fun save(mode: ListeningMode) {
        store.edit { it[KEY] = mode.key }
    }

    suspend fun load(): ListeningMode =
        ListeningMode.fromKey(store.data.first()[KEY] ?: "active")
}
