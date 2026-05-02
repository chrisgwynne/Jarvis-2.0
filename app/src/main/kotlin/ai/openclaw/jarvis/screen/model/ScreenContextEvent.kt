package ai.openclaw.jarvis.screen.model

/**
 * One foreground-app or screen-content change. Emitted by
 * [ai.openclaw.jarvis.screen.ForegroundAppTracker] (with
 * `extractedText` left null) and enriched in-place by
 * [ai.openclaw.jarvis.screen.service.ScreenAwarenessService] when
 * accessibility events arrive.
 *
 * `extractedText` is intentionally a tiny digest, not a full screen
 * dump — see ScreenContentExtractor for the redaction rules.
 */
data class ScreenContextEvent(
    val packageName: String,
    val appLabel: String,
    val category: AppCategory,
    val pageTitle: String? = null,
    val url: String? = null,
    val extractedText: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val source: Source = Source.FOREGROUND_TRACKER,
) {
    enum class Source { FOREGROUND_TRACKER, ACCESSIBILITY, SCREENSHOT }
}

/**
 * High-level meaning derived from one or more [ScreenContextEvent]s by
 * [ai.openclaw.jarvis.screen.ContextInterpreter]. Whatever we send to
 * OpenClaw is built from this — never raw screen text.
 */
data class InterpretedContext(
    val packageName: String,
    val appLabel: String,
    val category: AppCategory,
    val pageType: PageType,
    val summary: String,                       // human-readable
    val pageTitle: String? = null,
    val url: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

enum class PageType {
    LISTENING_TO_MUSIC,
    BROWSING_LISTINGS,
    READING_ARTICLE,
    MESSAGING,
    WRITING_EMAIL,
    READING_EMAIL,
    NAVIGATING,
    PHOTO_GALLERY,
    PRODUCTIVITY_DOC,
    UNKNOWN,
}

/**
 * A screenshot Android just saved. Fed to the auto-analysis path so
 * `screenshot taken → send to OpenClaw → present result` works without
 * a wake-word.
 */
data class ScreenshotCaptured(
    val uri: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val source: Source = Source.MEDIA_STORE,
) {
    enum class Source { MEDIA_STORE, FILE_OBSERVER, USER_INITIATED }
}
