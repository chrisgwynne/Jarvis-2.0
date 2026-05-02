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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trustedContactsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "jarvis_trusted_contacts")

@Serializable
data class TrustedContact(
    val displayName: String,
    val phoneOrAlias: String,
    val canAutoCall: Boolean = false,
    val canAutoSms: Boolean = false,
    val canAutoWhatsApp: Boolean = false,
)

/**
 * Stores trusted contacts for whom confirmation can be skipped.
 * Calls/SMS to trusted contacts with `canAutoCall` / `canAutoSms = true`
 * execute without a confirmation dialog.
 */
@Singleton
class TrustedContactsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.trustedContactsDataStore
    private val key   = stringPreferencesKey("trusted_contacts")
    private val json  = Json { ignoreUnknownKeys = true }
    private val ser   = ListSerializer(TrustedContact.serializer())

    val contacts: Flow<List<TrustedContact>> = store.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString(ser, raw) }.getOrDefault(emptyList())
    }

    suspend fun add(contact: TrustedContact) = store.edit { prefs ->
        val list = read(prefs).toMutableList()
        list.removeAll { it.phoneOrAlias == contact.phoneOrAlias }
        list.add(contact)
        prefs[key] = json.encodeToString(ser, list)
    }

    suspend fun remove(phoneOrAlias: String) = store.edit { prefs ->
        val list = read(prefs).filter { it.phoneOrAlias != phoneOrAlias }
        prefs[key] = json.encodeToString(ser, list)
    }

    suspend fun isTrustedForCall(nameOrNumber: String): Boolean {
        val lower = nameOrNumber.lowercase().trim()
        return contacts.first().any { c ->
            (c.canAutoCall) &&
            (c.displayName.lowercase() == lower || c.phoneOrAlias.lowercase() == lower)
        }
    }

    suspend fun isTrustedForSms(nameOrNumber: String): Boolean {
        val lower = nameOrNumber.lowercase().trim()
        return contacts.first().any { c ->
            (c.canAutoSms) &&
            (c.displayName.lowercase() == lower || c.phoneOrAlias.lowercase() == lower)
        }
    }

    suspend fun isTrustedForWhatsApp(nameOrNumber: String): Boolean {
        val lower = nameOrNumber.lowercase().trim()
        return contacts.first().any { c ->
            (c.canAutoWhatsApp) &&
            (c.displayName.lowercase() == lower || c.phoneOrAlias.lowercase() == lower)
        }
    }

    private fun read(prefs: Preferences): List<TrustedContact> {
        val raw = prefs[key] ?: return emptyList()
        return runCatching { json.decodeFromString(ser, raw) }.getOrDefault(emptyList())
    }
}
