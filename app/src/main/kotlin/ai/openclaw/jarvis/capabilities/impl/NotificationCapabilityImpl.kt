package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationCapability : Capability {
    suspend fun postNotification(title: String, body: String, channelId: String = "jarvis_general"): CapabilityResult<Unit>
}

@Singleton
class NotificationCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationCapability {

    override val id = "notification"
    override val description = "Post local notifications"
    override val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else emptyList()

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun isAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    override suspend fun postNotification(
        title: String,
        body: String,
        channelId: String,
    ): CapabilityResult<Unit> {
        if (!isAvailable()) {
            return capabilityFailure("PERMISSION_DENIED", "Notification permission not granted", true)
        }
        ensureChannel(channelId)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        return capabilitySuccess(Unit)
    }

    private fun ensureChannel(channelId: String) {
        if (notificationManager.getNotificationChannel(channelId) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
