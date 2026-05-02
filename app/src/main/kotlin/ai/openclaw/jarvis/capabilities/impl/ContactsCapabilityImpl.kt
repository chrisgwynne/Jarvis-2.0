package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

data class Contact(val name: String, val phone: String?)

interface ContactsCapability : Capability {
    suspend fun findContact(query: String): CapabilityResult<List<Contact>>
}

@Singleton
class ContactsCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactsCapability {

    override val id = "contacts"
    override val description = "Search device contacts by name"
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    override fun isAvailable() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override suspend fun findContact(query: String): CapabilityResult<List<Contact>> {
        if (!isAvailable()) {
            return capabilityFailure("PERMISSION_DENIED", "Contacts permission not granted", true)
        }
        if (query.isBlank()) {
            return capabilitySuccess(emptyList())
        }
        return try {
            val results = mutableListOf<Contact>()
            // Append `?limit=N` to the URI so the contacts provider caps
            // its own result-set walk regardless of how broad the LIKE is.
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                .buildUpon().appendQueryParameter("limit", MAX_RESULTS.toString()).build()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIdx < 0 || phoneIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                        ?: continue
                    val phone = cursor.getString(phoneIdx)?.takeIf { it.isNotBlank() }
                    results.add(Contact(name, phone))
                }
            }
            capabilitySuccess(results)
        } catch (e: SecurityException) {
            // Permission revoked between isAvailable() and the query.
            capabilityFailure("PERMISSION_DENIED", "Contacts permission revoked", true)
        } catch (e: Exception) {
            capabilityFailure("CONTACTS_ERROR", e.message ?: "Failed to query contacts")
        }
    }

    companion object {
        private const val MAX_RESULTS = 25
    }
}
