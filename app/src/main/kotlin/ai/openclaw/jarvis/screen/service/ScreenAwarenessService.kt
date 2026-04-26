package ai.openclaw.jarvis.screen.service

import ai.openclaw.jarvis.screen.ForegroundAppTracker
import ai.openclaw.jarvis.screen.ScreenContextBus
import ai.openclaw.jarvis.screen.model.AppCategorisation
import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Optional accessibility service that enriches the [ScreenContextBus]
 * with title / URL / extracted text for whatever's on screen.
 *
 * The user grants this from system settings — without it, the
 * [ForegroundAppTracker] still emits coarse events but no inner content
 * is extracted.
 *
 * The service short-circuits whenever:
 *   - screen-awareness is disabled in settings
 *   - the foreground package is sensitive (banking / passwords)
 *   - the package isn't allow-listed by the user (when whitelist non-empty)
 *
 * It never persists raw accessibility dumps; the extractor enforces
 * size + credential-field rules before anything leaves this class.
 */
@AndroidEntryPoint
class ScreenAwarenessService : AccessibilityService() {

    @Inject lateinit var settingsSource: ScreenAwarenessSettingsSource
    @Inject lateinit var extractor: ScreenContentExtractor
    @Inject lateinit var foregroundTracker: ForegroundAppTracker
    @Inject lateinit var bus: ScreenContextBus

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Tighten the runtime config — defaults are too noisy.
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 500
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val s = settingsSource.current()
        if (!s.enabled) return
        val pkg = e.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return

        // Early-exit for sensitive / blacklisted / non-whitelisted apps.
        if (pkg in s.blacklist) return
        if (s.whitelist.isNotEmpty() && pkg !in s.whitelist) return
        val category = AppCategorisation.classify(pkg)
        if (category in s.excludeCategories) return

        // First fire a coarse foreground event so consumers see the change.
        foregroundTracker.emitNow(pkg)

        // Then attempt the extract. rootInActiveWindow can be null briefly
        // during transitions; that's fine — bus.publish handles partial events.
        val extract = runCatching { extractor.extract(rootInActiveWindow) }
            .getOrDefault(ScreenContentExtractor.Extract())

        bus.publish(
            ScreenContextEvent(
                packageName = pkg,
                appLabel = appLabel(pkg),
                category = category,
                pageTitle = extract.pageTitle,
                url = extract.url,
                extractedText = extract.text,
                source = ScreenContextEvent.Source.ACCESSIBILITY,
            )
        )
    }

    override fun onInterrupt() {
        // No long-running tasks to abort — accessibility events are point-in-time.
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm: PackageManager = applicationContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
