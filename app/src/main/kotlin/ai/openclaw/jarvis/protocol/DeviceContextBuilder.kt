package ai.openclaw.jarvis.protocol

import ai.openclaw.jarvis.audio.AudioRouteManager
import ai.openclaw.jarvis.capabilities.CapabilityRegistry
import ai.openclaw.jarvis.protocol.model.DeviceContext
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the [DeviceContext] block sent in every [JarvisLiveRequest].
 * Best-effort: every field has a "?"/-1/empty fallback so a partial
 * snapshot still serialises cleanly when permissions are missing.
 */
@Singleton
class DeviceContextBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRouter: AudioRouteManager,
    private val caps: CapabilityRegistry,
) {
    fun build(): DeviceContext {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val charging = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val network = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return@runCatching "offline"
            val nc = cm.getNetworkCapabilities(net) ?: return@runCatching "unknown"
            when {
                nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        }.getOrDefault("unknown")

        val screen = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val ks = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            when {
                !pm.isInteractive -> "off"
                ks.isKeyguardLocked -> "locked"
                else -> "unlocked"
            }
        }.getOrDefault("unknown")

        val foregroundApp = runCatching { foregroundPackage() }.getOrDefault("unknown")

        val locationLabel = runCatching { caps.location.getLocationLabel() }.getOrDefault("unknown")

        return DeviceContext(
            battery = percent,
            charging = charging,
            screenState = screen,
            foregroundApp = foregroundApp,
            network = network,
            locationLabel = locationLabel,
        )
    }

    /**
     * Best-effort foreground package name. Uses UsageStatsManager when the
     * QUERY_USAGE_STATS-equivalent grant is in place; otherwise falls back
     * to the running-tasks heuristic. Returns "unknown" if neither works.
     */
    @Suppress("DEPRECATION")
    private fun foregroundPackage(): String {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usage?.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60_000, now)
        val recent = stats?.maxByOrNull { it.lastTimeUsed }
        if (recent?.packageName?.isNotBlank() == true) return recent.packageName

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val proc = am?.runningAppProcesses?.firstOrNull()
        return proc?.processName ?: "unknown"
    }
}
