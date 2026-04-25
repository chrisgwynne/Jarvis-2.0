package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

interface CallsCapability : Capability {
    /**
     * Initiate a phone call. Always requires confirmation unless trustedMode.
     */
    fun buildCallIntent(phoneNumber: String): CapabilityResult<Intent>
}

@Singleton
class CallsCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CallsCapability {

    override val id = "calls"
    override val description = "Place phone calls (requires confirmation)"
    override val requiredPermissions = listOf(Manifest.permission.CALL_PHONE)

    override fun isAvailable() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    override fun buildCallIntent(phoneNumber: String): CapabilityResult<Intent> {
        if (!isAvailable()) {
            return capabilityFailure("PERMISSION_DENIED", "Call permission not granted", true)
        }
        if (phoneNumber.isBlank()) {
            return capabilityFailure("INVALID_NUMBER", "Phone number is blank")
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return capabilitySuccess(intent)
    }
}
