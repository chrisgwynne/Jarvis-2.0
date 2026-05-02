package ai.openclaw.jarvis.screen.service

import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls a *small* digest from the visible accessibility tree:
 *   - page title (toolbar / heading-style nodes)
 *   - URL when the focused node looks like a browser address bar
 *   - up to [MAX_TEXT_NODES] visible text snippets
 *
 * Hard rules baked in here:
 *   - never read isPassword nodes
 *   - never read EditText with InputType containing PASSWORD bits
 *   - cap total returned text at [MAX_CHARS] characters
 *   - drop anything matching obvious credential field labels
 *
 * No file I/O, no Bitmap capture — that path lives in the screenshot
 * observer. This is purely an accessibility-tree scrape.
 */
@Singleton
class ScreenContentExtractor @Inject constructor() {

    data class Extract(
        val pageTitle: String? = null,
        val url: String? = null,
        val text: String? = null,
    )

    fun extract(root: AccessibilityNodeInfo?): Extract {
        if (root == null) return Extract()
        val texts = mutableListOf<String>()
        var title: String? = null
        var url: String? = null

        walk(root) { node ->
            if (node.isPassword) return@walk
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            // Browser URL bars universally have ids ending in `url_bar` /
            // `url_text` / similar across Chrome forks; cover the common ones.
            if (url == null && (viewId.endsWith("url_bar") ||
                    viewId.endsWith("url_field") ||
                    viewId.endsWith("urlbar") ||
                    viewId.endsWith("location_bar_edit_text"))) {
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let { url = it }
                return@walk
            }
            // Heading-ish: short text near the top of the tree, treat as title.
            if (title == null && node.isHeading()) {
                node.text?.toString()?.takeIf { it.isNotBlank() && it.length < 80 }
                    ?.let { title = it }
                return@walk
            }
            // Generic visible text — skip credential-shaped labels.
            val raw = node.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) return@walk
            if (looksLikeCredentialLabel(raw)) return@walk
            if (raw.length > 200) return@walk     // skip likely paragraphs
            texts += raw
        }

        val joined = texts.distinct().take(MAX_TEXT_NODES).joinToString(" • ")
            .take(MAX_CHARS)
            .takeIf { it.isNotBlank() }

        return Extract(pageTitle = title, url = url, text = joined)
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    private fun walk(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, visit)
        }
    }

    private fun AccessibilityNodeInfo.isHeading(): Boolean {
        return isHeading || className?.contains("Toolbar", ignoreCase = true) == true
    }

    private fun looksLikeCredentialLabel(s: String): Boolean {
        val l = s.trim().lowercase()
        return CREDENTIAL_TOKENS.any { l == it || l.startsWith("$it ") || l.endsWith(" $it") }
    }

    companion object {
        private const val MAX_TEXT_NODES = 16
        private const val MAX_CHARS = 800
        private val CREDENTIAL_TOKENS = listOf(
            "password", "passcode", "pin", "otp", "verification code",
            "card number", "cvv", "cvc", "social security", "national insurance",
        )
    }
}
