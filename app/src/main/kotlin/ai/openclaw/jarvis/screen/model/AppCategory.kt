package ai.openclaw.jarvis.screen.model

/**
 * Coarse category Jarvis cares about for proactive triggers. Mapped from
 * package name in [AppCategorisation.classify].
 *
 * SENSITIVE is the only special-cased value — once classified as such the
 * passive-assist layer suppresses every form of suggestion regardless of
 * other settings (matches the spec's "no suggestions inside banking /
 * password apps" rule).
 */
enum class AppCategory {
    BROWSER,
    MESSAGING,
    EMAIL,
    MEDIA,
    SHOPPING,
    PRODUCTIVITY,
    SOCIAL,
    NAVIGATION,
    PHOTO,
    SENSITIVE,         // banking, password managers, authenticators
    SYSTEM,
    UNKNOWN,
}

/**
 * Pure-data classifier; no Android dependencies so it's trivially
 * unit-testable. Adding a new package only requires extending the maps
 * below.
 */
object AppCategorisation {

    private val EXACT: Map<String, AppCategory> = mapOf(
        // Browsers
        "com.android.chrome" to AppCategory.BROWSER,
        "org.mozilla.firefox" to AppCategory.BROWSER,
        "com.opera.browser" to AppCategory.BROWSER,
        "com.brave.browser" to AppCategory.BROWSER,
        // Messaging
        "com.whatsapp" to AppCategory.MESSAGING,
        "org.telegram.messenger" to AppCategory.MESSAGING,
        "com.google.android.apps.messaging" to AppCategory.MESSAGING,
        "com.android.mms" to AppCategory.MESSAGING,
        // Email
        "com.google.android.gm" to AppCategory.EMAIL,
        "com.microsoft.office.outlook" to AppCategory.EMAIL,
        "com.fastmail.app" to AppCategory.EMAIL,
        // Media
        "com.spotify.music" to AppCategory.MEDIA,
        "com.google.android.youtube" to AppCategory.MEDIA,
        "tv.plex.android" to AppCategory.MEDIA,
        // Shopping
        "com.etsy.android" to AppCategory.SHOPPING,
        "com.amazon.mShop.android.shopping" to AppCategory.SHOPPING,
        // Productivity
        "com.google.android.apps.docs.editors.docs" to AppCategory.PRODUCTIVITY,
        "com.notion.id" to AppCategory.PRODUCTIVITY,
        // Social
        "com.instagram.android" to AppCategory.SOCIAL,
        "com.twitter.android" to AppCategory.SOCIAL,
        "com.reddit.frontpage" to AppCategory.SOCIAL,
        // Navigation
        "com.google.android.apps.maps" to AppCategory.NAVIGATION,
        "com.waze" to AppCategory.NAVIGATION,
        // Photo
        "com.google.android.apps.photos" to AppCategory.PHOTO,
        // Banking / sensitive — the SENSITIVE check below should be liberal
    )

    /**
     * Substring patterns that imply SENSITIVE regardless of exact match.
     * Anything banking-, wallet-, password-, or authenticator-shaped.
     */
    private val SENSITIVE_PATTERNS = listOf(
        ".bank", ".banking", ".banco", ".monzo", ".starling",
        ".barclays", ".lloydsbank", ".natwest", ".santander", ".chase",
        ".revolut", ".paypal", ".wise.com",
        ".wallet", ".keepass", ".bitwarden", ".lastpass", ".1password",
        ".onepassword", ".dashlane", ".authenticator", ".microsoft.azure.authenticator",
        "google.android.apps.authenticator",
    )

    fun classify(packageName: String): AppCategory {
        if (packageName.isBlank()) return AppCategory.UNKNOWN
        // Sensitive substrings first — never let a coincidental EXACT match
        // (e.g. some banking app added to MESSAGING by mistake) win.
        if (SENSITIVE_PATTERNS.any { packageName.contains(it, ignoreCase = true) }) {
            return AppCategory.SENSITIVE
        }
        EXACT[packageName]?.let { return it }
        if (packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.gms")) {
            return AppCategory.SYSTEM
        }
        return AppCategory.UNKNOWN
    }
}
