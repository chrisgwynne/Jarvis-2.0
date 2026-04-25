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
        return try {
            val results = mutableListOf<Contact>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    results.add(Contact(cursor.getString(nameIdx), cursor.getString(phoneIdx)))
                }
            }
            capabilitySuccess(results)
        } catch (e: Exception) {
            capabilityFailure("CONTACTS_ERROR", e.message ?: "Failed to query contacts")
        }
    }
}
