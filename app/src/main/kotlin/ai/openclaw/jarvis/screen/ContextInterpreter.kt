package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.screen.model.AppCategory
import ai.openclaw.jarvis.screen.model.InterpretedContext
import ai.openclaw.jarvis.screen.model.PageType
import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates a raw [ScreenContextEvent] into an [InterpretedContext]
 * — the *meaning* of what's on screen.
 *
 * Intentionally rule-based and tiny:
 *   - category-driven default (MEDIA → "listening to music")
 *   - URL hints for the BROWSER case (etsy.com → BROWSING_LISTINGS)
 *   - title hints for EMAIL apps (drafts → WRITING_EMAIL)
 *
 * No ML, no remote calls.
 */
@Singleton
class ContextInterpreter @Inject constructor() {

    fun interpret(event: ScreenContextEvent): InterpretedContext {
        val (pageType, summary) = when (event.category) {
            AppCategory.MEDIA -> PageType.LISTENING_TO_MUSIC to
                "${event.appLabel}: listening to media"
            AppCategory.MESSAGING -> PageType.MESSAGING to
                "${event.appLabel}: messaging${event.pageTitle?.let { " — $it" } ?: ""}"
            AppCategory.EMAIL -> emailPage(event)
            AppCategory.BROWSER -> browserPage(event)
            AppCategory.SHOPPING -> PageType.BROWSING_LISTINGS to
                "${event.appLabel}: browsing listings"
            AppCategory.NAVIGATION -> PageType.NAVIGATING to
                "${event.appLabel}: navigating"
            AppCategory.PHOTO -> PageType.PHOTO_GALLERY to
                "${event.appLabel}: viewing photos"
            AppCategory.PRODUCTIVITY -> PageType.PRODUCTIVITY_DOC to
                "${event.appLabel}: working on a document"
            AppCategory.SOCIAL -> PageType.READING_ARTICLE to
                "${event.appLabel}: scrolling social"
            AppCategory.SENSITIVE,
            AppCategory.SYSTEM,
            AppCategory.UNKNOWN -> PageType.UNKNOWN to
                "${event.appLabel}: foreground"
        }
        return InterpretedContext(
            packageName = event.packageName,
            appLabel = event.appLabel,
            category = event.category,
            pageType = pageType,
            summary = summary,
            pageTitle = event.pageTitle,
            url = event.url,
            timestampMillis = event.timestampMillis,
        )
    }

    private fun emailPage(e: ScreenContextEvent): Pair<PageType, String> {
        val title = e.pageTitle?.lowercase().orEmpty()
        return when {
            "compose" in title || "draft" in title -> PageType.WRITING_EMAIL to
                "${e.appLabel}: writing email"
            else -> PageType.READING_EMAIL to "${e.appLabel}: reading email"
        }
    }

    private fun browserPage(e: ScreenContextEvent): Pair<PageType, String> {
        val u = e.url?.lowercase().orEmpty()
        return when {
            u.isBlank() -> PageType.READING_ARTICLE to "${e.appLabel}: browsing"
            "etsy.com" in u -> PageType.BROWSING_LISTINGS to
                "${e.appLabel}: browsing Etsy listings"
            "amazon" in u -> PageType.BROWSING_LISTINGS to
                "${e.appLabel}: browsing Amazon"
            "mail." in u || "/mail" in u -> PageType.READING_EMAIL to
                "${e.appLabel}: reading email in browser"
            "docs.google" in u || "notion.so" in u -> PageType.PRODUCTIVITY_DOC to
                "${e.appLabel}: working on a document"
            else -> PageType.READING_ARTICLE to "${e.appLabel}: reading a page"
        }
    }
}
