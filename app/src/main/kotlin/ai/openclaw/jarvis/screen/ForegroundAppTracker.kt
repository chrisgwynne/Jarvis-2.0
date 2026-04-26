package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.screen.model.AppCategorisation
import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Light-touch foreground-app watcher. Polls UsageStatsManager every
 * [POLL_PERIOD_MS] (only when screen-awareness is enabled), debounces
 * rapid flips so a half-second back-and-forth doesn't flood downstream
 * consumers, and emits one [ScreenContextEvent] per *settled* foreground
 * app change.
 *
 * The accessibility service ([ai.openclaw.jarvis.screen.service.ScreenAwarenessService])
 * provides finer-grained events when the user has granted accessibility
 * — when that arrives, the tracker still fires the coarse event but the
 * service enriches it with title / URL / extracted text.
 */
@Singleton
class ForegroundAppTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsSource: ScreenAwarenessSettingsSource,
) {
    private val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val pm: PackageManager = context.packageManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<ScreenContextEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ScreenContextEvent> = _events.asSharedFlow()

    @Volatile private var lastEmittedPackage: String? = null
    @Volatile private var lastEmittedAtMillis: Long = 0L
    @Volatile private var running = false

    /** Idempotent. Stops itself if screen-awareness setting flips off. */
    fun start() {
        if (running) return
        running = true
        scope.launch {
            while (isActive) {
                if (settingsSource.current().enabled) sample()
                delay(POLL_PERIOD_MS)
            }
        }
    }

    /** Force-emit the current foreground app (called by the accessibility
     *  service when it sees a window change). */
    fun emitNow(pkg: String) = handle(pkg)

    private fun sample() {
        val pkg = currentForegroundPackage() ?: return
        handle(pkg)
    }

    private fun handle(pkg: String) {
        val now = System.currentTimeMillis()
        if (pkg == lastEmittedPackage && now - lastEmittedAtMillis < DEBOUNCE_MS) return
        lastEmittedPackage = pkg
        lastEmittedAtMillis = now

        val s = settingsSource.current()
        if (!s.enabled) return
        if (pkg in s.blacklist) return
        if (s.whitelist.isNotEmpty() && pkg !in s.whitelist) return
        val category = AppCategorisation.classify(pkg)
        if (category in s.excludeCategories) return

        _events.tryEmit(
            ScreenContextEvent(
                packageName = pkg,
                appLabel = appLabelOf(pkg),
                category = category,
                timestampMillis = now,
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun currentForegroundPackage(): String? {
        val um = usage ?: return null
        val now = System.currentTimeMillis()
        // Look back a small window so we catch the most recent change.
        val stats = runCatching {
            um.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60_000, now)
        }.getOrNull().orEmpty()
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName?.takeIf { it.isNotBlank() }
    }

    private fun appLabelOf(pkg: String): String = runCatching {
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val POLL_PERIOD_MS = 5_000L
        private const val DEBOUNCE_MS = 1_500L
    }
}
