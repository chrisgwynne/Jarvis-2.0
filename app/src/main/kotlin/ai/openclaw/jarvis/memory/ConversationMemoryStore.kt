package ai.openclaw.jarvis.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.memoryDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "conversation_memory")

/**
 * Persists conversation history as a rolling JSON list.
 * Keeps the last [MAX_ENTRIES] exchanges so the context stays bounded.
 */
@Singleton
class ConversationMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MAX_ENTRIES = 20
        private val KEY = stringPreferencesKey("memory_entries")
    }

    private val store = context.memoryDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val entries: Flow<List<MemoryEntry>> = store.data.map { prefs ->
        prefs[KEY]?.let { raw ->
            try { json.decodeFromString<List<MemoryEntry>>(raw) } catch (_: Exception) { emptyList() }
        } ?: emptyList()
    }

    suspend fun add(entry: MemoryEntry) {
        store.edit { prefs ->
            val current = prefs[KEY]?.let { raw ->
                try { json.decodeFromString<List<MemoryEntry>>(raw) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            val updated = (current + entry).takeLast(MAX_ENTRIES)
            prefs[KEY] = json.encodeToString(updated)
        }
    }

    suspend fun clear() = store.edit { it.remove(KEY) }
}
