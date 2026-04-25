package ai.openclaw.jarvis.capabilities.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

interface WhatsAppCapability : Capability {
    /**
     * Open a WhatsApp chat with a phone number and pre-fill the message.
     * The user still has to tap Send inside WhatsApp.
     * For auto-send without UI, use the WhatsApp Business API (requires API key).
     */
    fun buildOpenChatIntent(phoneNumber: String, prefillMessage: String): CapabilityResult<Intent>

    /** Send directly via WhatsApp share intent (works for any contact). */
    fun buildShareIntent(text: String): CapabilityResult<Intent>

    fun isWhatsAppInstalled(): Boolean
}

@Singleton
class WhatsAppCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WhatsAppCapability {

    override val id = "whatsapp"
    override val description = "Open WhatsApp chat or share text via WhatsApp"
    override val requiredPermissions: List<String> = emptyList()

    private val WA_PACKAGE = "com.whatsapp"
    private val WA_BIZ_PACKAGE = "com.whatsapp.w4b"

    override fun isAvailable() = isWhatsAppInstalled()

    override fun isWhatsAppInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(WA_PACKAGE, 0)
            true
        }.getOrDefault(false) || runCatching {
            context.packageManager.getPackageInfo(WA_BIZ_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    override fun buildOpenChatIntent(
        phoneNumber: String,
        prefillMessage: String,
    ): CapabilityResult<Intent> {
        if (!isWhatsAppInstalled()) {
            return capabilityFailure("WHATSAPP_NOT_INSTALLED", "WhatsApp is not installed on this device")
        }
        val cleaned = phoneNumber.replace(Regex("""[^\d+]"""), "")
        if (cleaned.isBlank()) {
            return capabilityFailure("INVALID_NUMBER", "Phone number is blank after cleaning")
        }
        val uri = Uri.parse("https://wa.me/$cleaned?text=${Uri.encode(prefillMessage)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return capabilitySuccess(intent)
    }

    override fun buildShareIntent(text: String): CapabilityResult<Intent> {
        if (!isWhatsAppInstalled()) {
            return capabilityFailure("WHATSAPP_NOT_INSTALLED", "WhatsApp is not installed on this device")
        }
        val pkg = if (runCatching { context.packageManager.getPackageInfo(WA_PACKAGE, 0); true }.getOrDefault(false))
            WA_PACKAGE else WA_BIZ_PACKAGE
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(pkg)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return capabilitySuccess(intent)
    }
}
