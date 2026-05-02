package ai.openclaw.jarvis.screen.store

import ai.openclaw.jarvis.screen.model.AppCategory

/**
 * User-controlled tuning for the screen-awareness subsystem.
 *
 * `whitelist` and `blacklist` are package-name sets:
 *   - blacklist wins over whitelist
 *   - empty whitelist + empty blacklist = "watch every non-sensitive app"
 *   - non-empty whitelist = "watch only these (minus blacklist)"
 *
 * Sensitive-category apps (banking, password managers, authenticators)
 * are always excluded regardless of the lists — no override available.
 */
data class ScreenAwarenessSettings(
    val enabled: Boolean = false,
    val whitelist: Set<String> = emptySet(),
    val blacklist: Set<String> = emptySet(),
    val screenshotAutoAnalyse: Boolean = true,
    val storeScreenshots: Boolean = false,
    val retentionHours: Int = 24,
    val voicePromptOnAnalysis: Boolean = false,
    val excludeCategories: Set<AppCategory> = setOf(AppCategory.SENSITIVE),
)

interface ScreenAwarenessSettingsSource {
    fun current(): ScreenAwarenessSettings
}
