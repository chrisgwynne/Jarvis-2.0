package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.screen.model.ScreenshotCaptured
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the MediaStore for new entries inside Pictures/Screenshots
 * (or DCIM/Screenshots on some OEMs). When one appears within
 * [DEDUPE_WINDOW_MS] of the last reported one, it's ignored — phones
 * occasionally fire several MediaStore notifications per shot.
 *
 * This is the spec's "screenshot detected → auto-send to OpenClaw" hook;
 * the actual sending lives in [ScreenshotAutoAnalyser] so this class
 * stays observation-only.
 */
@Singleton
class ScreenshotObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsSource: ScreenAwarenessSettingsSource,
    private val bus: ScreenContextBus,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var registered = false
    @Volatile private var lastEmittedAt: Long = 0L
    @Volatile private var lastEmittedUri: String? = null

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!settingsSource.current().enabled) return
            val s = uri?.toString() ?: return
            if (!s.contains("/screenshot", ignoreCase = true) &&
                !s.endsWith("/external/images/media")) return
            val now = System.currentTimeMillis()
            if (s == lastEmittedUri && now - lastEmittedAt < DEDUPE_WINDOW_MS) return
            lastEmittedUri = s
            lastEmittedAt = now
            bus.publishScreenshot(
                ScreenshotCaptured(uri = s, timestampMillis = now,
                    source = ScreenshotCaptured.Source.MEDIA_STORE)
            )
        }
    }

    /** Idempotent — registers a single observer on the images table. */
    fun start() {
        if (registered) return
        registered = true
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    companion object {
        private const val DEDUPE_WINDOW_MS = 2_000L
    }
}
