package ai.openclaw.jarvis.capabilities.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

interface AppsCapability : Capability {
    fun buildLaunchIntent(appNameOrPackage: String): CapabilityResult<Intent>
    fun listInstalledApps(): List<String>
}

@Singleton
class AppsCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppsCapability {

    override val id = "apps"
    override val description = "Launch installed apps by name or package"
    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable() = true

    override fun buildLaunchIntent(appNameOrPackage: String): CapabilityResult<Intent> {
        val pm = context.packageManager

        // Try exact package first
        val exactIntent = pm.getLaunchIntentForPackage(appNameOrPackage)
        if (exactIntent != null) {
            exactIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return capabilitySuccess(exactIntent)
        }

        // Fuzzy match by app label
        val query = appNameOrPackage.lowercase().trim()
        val match = pm.getInstalledApplications(PackageManager.GET_META_DATA).firstOrNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            label.contains(query) || appInfo.packageName.lowercase().contains(query)
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                ?: return capabilityFailure("APP_NOT_LAUNCHABLE", "$appNameOrPackage has no launcher activity")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            capabilitySuccess(launchIntent)
        } else {
            capabilityFailure("APP_NOT_FOUND", "No installed app matching '$appNameOrPackage'")
        }
    }

    override fun listInstalledApps(): List<String> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { pm.getApplicationLabel(it).toString() }
            .sorted()
    }
}
