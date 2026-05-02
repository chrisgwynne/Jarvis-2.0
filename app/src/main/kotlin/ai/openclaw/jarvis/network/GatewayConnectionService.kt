package ai.openclaw.jarvis.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import ai.openclaw.jarvis.MainActivity
import ai.openclaw.jarvis.data.models.GatewayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Persistent foreground service that keeps the OpenClaw WebSocket connection
 * alive through phone lock, Doze, and Wi-Fi power-saving.
 *
 * Three mechanisms work together:
 *  1. Foreground service (type=dataSync) — prevents the OS from killing the
 *     process or deferring work via Doze.
 *  2. WifiLock(WIFI_MODE_FULL_HIGH_PERF) — prevents the Wi-Fi radio from
 *     parking when the screen is off, which would silently drop TCP connections.
 *  3. ConnectivityManager.NetworkCallback — triggers an immediate reconnect
 *     the moment network becomes available rather than waiting for the
 *     exponential backoff timer to fire.
 *
 * Started unconditionally from [ai.openclaw.jarvis.JarvisApp.onCreate] and
 * from [ai.openclaw.jarvis.receiver.BootReceiver]. START_STICKY ensures the
 * OS restarts it if it is ever killed.
 */
@AndroidEntryPoint
class GatewayConnectionService : Service() {

    @Inject lateinit var openClawClient: OpenClawClient

    companion object {
        const val ACTION_START = "ai.openclaw.jarvis.ACTION_START_GATEWAY"
        const val ACTION_STOP  = "ai.openclaw.jarvis.ACTION_STOP_GATEWAY"

        private const val CHANNEL_ID      = "jarvis_gateway"
        private const val NOTIFICATION_ID = 2002
        private const val TAG             = "GatewayConnectionService"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, GatewayConnectionService::class.java)
                    .apply { action = ACTION_START }
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var connectivityManager: ConnectivityManager

    // ─── Service lifecycle ────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to OpenClaw…"))

        acquireWifiLock()
        registerNetworkCallback()
        observeGatewayState()

        Log.d(TAG, "Gateway connection service started")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Gateway connection service destroyed")
        scope.cancel()
        releaseWifiLock()
        unregisterNetworkCallback()
        super.onDestroy()
    }

    // ─── Wi-Fi lock ───────────────────────────────────────────────────────────

    private fun acquireWifiLock() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "jarvis:gateway")
            wifiLock?.acquire()
            Log.d(TAG, "Wi-Fi lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't acquire Wi-Fi lock: ${e.message}")
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
        wifiLock = null
    }

    // ─── Network callback ─────────────────────────────────────────────────────

    private fun registerNetworkCallback() {
        connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available — checking gateway connection")
                // If the connection dropped while the network was gone, reconnect
                // immediately instead of waiting for the backoff timer.
                if (!openClawClient.isConnected()) {
                    Log.d(TAG, "Not connected — triggering reconnect")
                    openClawClient.disconnect()
                    openClawClient.connect()
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
            }
        }.also { cb ->
            connectivityManager.registerNetworkCallback(request, cb)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null
    }

    // ─── Gateway state observation ────────────────────────────────────────────

    private fun observeGatewayState() {
        scope.launch {
            openClawClient.gatewayState.collect { state ->
                val text = when (state) {
                    GatewayState.CONNECTED      -> "Connected to OpenClaw"
                    GatewayState.CONNECTING     -> "Connecting to OpenClaw…"
                    GatewayState.PAIRING        -> "Waiting for approval…"
                    GatewayState.DISCONNECTED   -> "Reconnecting to OpenClaw…"
                    GatewayState.OFFLINE_QUEUED -> "Offline — commands queued"
                }
                updateNotification(text)
            }
        }
    }

    // ─── Notification helpers ─────────────────────────────────────────────────

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Jarvis Gateway",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps Jarvis connected to OpenClaw in the background"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(status: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    private fun updateNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }
}
