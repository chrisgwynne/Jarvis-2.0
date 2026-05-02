package ai.openclaw.jarvis.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ai.openclaw.jarvis.data.models.QueuedEvent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.queueDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "jarvis_offline_queue")

/**
 * Persists session events when the Gateway is unreachable.
 * Serialises to a JSONL-style list in DataStore.
 * On reconnect, SessionEventLogger drains this queue.
 */
@Singleton
class OfflineQueueStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.queueDataStore
    private val queueKey = stringPreferencesKey("event_queue")

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(QueuedEvent.serializer())

    val queuedEvents: Flow<List<QueuedEvent>> = store.data.map { prefs ->
        val raw = prefs[queueKey] ?: return@map emptyList()
        runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    val queueSize: Flow<Int> = queuedEvents.map { it.size }

    suspend fun enqueue(event: ai.openclaw.jarvis.data.models.SessionEvent) {
        val queued = QueuedEvent(
            event = event,
            queuedAt = Instant.now().toString(),
        )
        store.edit { prefs ->
            val current = readList(prefs)
            prefs[queueKey] = json.encodeToString(listSerializer, current + queued)
        }
    }

    suspend fun dequeueAll(): List<QueuedEvent> {
        var result = emptyList<QueuedEvent>()
        store.edit { prefs ->
            result = readList(prefs)
            prefs[queueKey] = "[]"
        }
        return result
    }

    suspend fun isEmpty(): Boolean = store.data.first()[queueKey].let { raw ->
        raw == null || raw == "[]" || raw.isBlank()
    }

    private fun readList(prefs: Preferences): List<QueuedEvent> {
        val raw = prefs[queueKey] ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }
}
