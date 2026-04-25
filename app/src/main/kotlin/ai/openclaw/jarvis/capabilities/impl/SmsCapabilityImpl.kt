package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

interface SmsCapability : Capability {
    /**
     * Send an SMS. Always requires explicit confirmation from the caller
     * unless trustedMode is enabled in settings.
     */
    suspend fun sendSms(phoneNumber: String, message: String): CapabilityResult<Unit>
}

@Singleton
class SmsCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsCapability {

    override val id = "sms"
    override val description = "Send SMS messages (requires confirmation)"
    override val requiredPermissions = listOf(Manifest.permission.SEND_SMS)

    override fun isAvailable() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

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
}
