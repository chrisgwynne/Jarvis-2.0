package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestampMs: Long,
    val read: Boolean,
)

interface SmsCapability : Capability {
    suspend fun sendSms(phoneNumber: String, message: String): CapabilityResult<Unit>
    suspend fun readUnread(limit: Int = 10): CapabilityResult<List<SmsMessage>>
    suspend fun readAll(limit: Int = 20): CapabilityResult<List<SmsMessage>>
}

@Singleton
class SmsCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsCapability {

    override val id = "sms"
    override val description = "Send and read SMS messages"
    override val requiredPermissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)

    override fun isAvailable() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hasReadSms() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    override suspend fun sendSms(phoneNumber: String, message: String): CapabilityResult<Unit> {
        if (!isAvailable()) {
            return capabilityFailure("PERMISSION_DENIED", "SMS permission not granted", true)
        }
        if (phoneNumber.isBlank()) {
            return capabilityFailure("INVALID_NUMBER", "Phone number is blank")
        }
        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            capabilitySuccess(Unit)
        } catch (e: Exception) {
            capabilityFailure("SMS_SEND_ERROR", e.message ?: "Failed to send SMS")
        }
    }

    override suspend fun readUnread(limit: Int): CapabilityResult<List<SmsMessage>> =
        querySmsInbox(unreadOnly = true, limit = limit)

    override suspend fun readAll(limit: Int): CapabilityResult<List<SmsMessage>> =
        querySmsInbox(unreadOnly = false, limit = limit)

    private fun querySmsInbox(unreadOnly: Boolean, limit: Int): CapabilityResult<List<SmsMessage>> {
        if (!hasReadSms()) return capabilityFailure("PERMISSION_DENIED", "READ_SMS not granted", true)
        return try {
            val messages = mutableListOf<SmsMessage>()
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
            )
            val selection = if (unreadOnly) "${Telephony.Sms.READ} = 0" else null
            context.contentResolver.query(uri, projection, selection, null, "${Telephony.Sms.DATE} DESC")
                ?.use { cursor ->
                    val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        messages.add(
                            SmsMessage(
                                sender      = cursor.getString(addrIdx) ?: "Unknown",
                                body        = cursor.getString(bodyIdx) ?: "",
                                timestampMs = cursor.getLong(dateIdx),
                                read        = cursor.getInt(readIdx) != 0,
                            )
                        )
                        count++
                    }
                }
            capabilitySuccess(messages)
        } catch (e: Exception) {
            capabilityFailure("SMS_READ_ERROR", e.message ?: "Failed to read SMS inbox")
        }
    }
}
