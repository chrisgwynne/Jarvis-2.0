package ai.openclaw.jarvis.screen.store

import ai.openclaw.jarvis.screen.model.AppCategory
import ai.openclaw.jarvis.util.LazyHydrate
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed persistence for [ScreenAwarenessSettings].
 *
 * The whitelist / blacklist live as comma-separated strings to keep the
 * file format compatible with future additions; nothing here is
 * sensitive so plain prefs is fine.
 *
 * Lazy hydration: defaults have `enabled = false` — strictly the safest
 * behaviour, so the brief default-only window during startup is harmless.
 */
@Singleton
class ScreenAwarenessSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ScreenAwarenessSettingsSource {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(ScreenAwarenessSettings())
    val settings: StateFlow<ScreenAwarenessSettings> = _settings.asStateFlow()

    private val hydrate = LazyHydrate(_settings) { load() }
    init { hydrate.start() }

    override fun current(): ScreenAwarenessSettings = _settings.value

    fun update(transform: (ScreenAwarenessSettings) -> ScreenAwarenessSettings) {
        hydrate.markUpdated()
        val next = transform(_settings.value)
        save(next)
        _settings.value = next
    }

    fun addToWhitelist(packageName: String) =
        update { it.copy(whitelist = it.whitelist + packageName) }

    fun addToBlacklist(packageName: String) =
        update { it.copy(blacklist = it.blacklist + packageName) }

    fun removeFromWhitelist(packageName: String) =
        update { it.copy(whitelist = it.whitelist - packageName) }

    fun removeFromBlacklist(packageName: String) =
        update { it.copy(blacklist = it.blacklist - packageName) }

    private fun load() = ScreenAwarenessSettings(
        enabled = prefs.getBoolean(K_ENABLED, false),
        whitelist = prefs.getStringSet(K_WHITELIST, null).orEmpty().toSet(),
        blacklist = prefs.getStringSet(K_BLACKLIST, null).orEmpty().toSet(),
        screenshotAutoAnalyse = prefs.getBoolean(K_SCREENSHOT_AUTO, true),
        storeScreenshots = prefs.getBoolean(K_STORE_SHOTS, false),
        retentionHours = prefs.getInt(K_RETENTION, 24),
        voicePromptOnAnalysis = prefs.getBoolean(K_VOICE, false),
        excludeCategories = setOf(AppCategory.SENSITIVE),
    )

    private fun save(s: ScreenAwarenessSettings) {
        prefs.edit().apply {
            putBoolean(K_ENABLED, s.enabled)
            putStringSet(K_WHITELIST, s.whitelist)
            putStringSet(K_BLACKLIST, s.blacklist)
            putBoolean(K_SCREENSHOT_AUTO, s.screenshotAutoAnalyse)
            putBoolean(K_STORE_SHOTS, s.storeScreenshots)
            putInt(K_RETENTION, s.retentionHours)
            putBoolean(K_VOICE, s.voicePromptOnAnalysis)
        }.apply()
    }

    companion object {
        private const val FILE_NAME = "jarvis_screen_awareness_settings"
        private const val K_ENABLED = "enabled"
        private const val K_WHITELIST = "whitelist"
        private const val K_BLACKLIST = "blacklist"
        private const val K_SCREENSHOT_AUTO = "screenshot_auto"
        private const val K_STORE_SHOTS = "store_shots"
        private const val K_RETENTION = "retention_hours"
        private const val K_VOICE = "voice_prompt"
    }
}
