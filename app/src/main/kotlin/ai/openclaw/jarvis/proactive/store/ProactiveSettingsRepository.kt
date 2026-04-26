package ai.openclaw.jarvis.proactive.store

import ai.openclaw.jarvis.proactive.model.Aggressiveness
import ai.openclaw.jarvis.proactive.model.ProactiveSettings
import ai.openclaw.jarvis.proactive.model.QuietHours
import ai.openclaw.jarvis.proactive.model.SignalType
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists proactive settings (regular SharedPreferences — none of this
 * is sensitive). Exposes a [StateFlow] so the UI redraws on change
 * without having to re-read.
 *
 * The "don't suggest this again" set lives here too; it's per-suggestion
 * id, not per-signal, so a dismissed "Analyse last screenshot?" doesn't
 * also kill "Switch to voice mode?".
 */
@Singleton
class ProactiveSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ProactiveSettingsSource {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ProactiveSettings> = _settings.asStateFlow()

    override fun current(): ProactiveSettings = _settings.value

    fun update(transform: (ProactiveSettings) -> ProactiveSettings) {
        val next = transform(_settings.value)
        save(next)
        _settings.value = next
    }

    override fun suppress(suggestionId: String) = update {
        it.copy(suppressedSuggestionIds = it.suppressedSuggestionIds + suggestionId)
    }

    fun unsuppressAll() = update {
        it.copy(suppressedSuggestionIds = emptySet())
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private fun load(): ProactiveSettings {
        val perSignal = SignalType.values().associateWith { sig ->
            prefs.getBoolean(K_SIGNAL_PREFIX + sig.name, true)
        }
        return ProactiveSettings(
            enabled = prefs.getBoolean(K_ENABLED, true),
            aggressiveness = runCatching {
                Aggressiveness.valueOf(prefs.getString(K_AGG, Aggressiveness.MEDIUM.name) ?: Aggressiveness.MEDIUM.name)
            }.getOrDefault(Aggressiveness.MEDIUM),
            quietHours = QuietHours(
                enabled = prefs.getBoolean(K_Q_ENABLED, false),
                startHour = prefs.getInt(K_Q_START, 22),
                endHour = prefs.getInt(K_Q_END, 7),
            ),
            perSignal = perSignal,
            suppressedSuggestionIds = prefs.getStringSet(K_SUPPRESSED, null)?.toSet().orEmpty(),
        )
    }

    private fun save(s: ProactiveSettings) {
        prefs.edit().apply {
            putBoolean(K_ENABLED, s.enabled)
            putString(K_AGG, s.aggressiveness.name)
            putBoolean(K_Q_ENABLED, s.quietHours.enabled)
            putInt(K_Q_START, s.quietHours.startHour)
            putInt(K_Q_END, s.quietHours.endHour)
            for ((sig, on) in s.perSignal) putBoolean(K_SIGNAL_PREFIX + sig.name, on)
            putStringSet(K_SUPPRESSED, s.suppressedSuggestionIds)
        }.apply()
    }

    companion object {
        private const val FILE_NAME = "jarvis_proactive_settings"
        private const val K_ENABLED = "enabled"
        private const val K_AGG = "aggressiveness"
        private const val K_Q_ENABLED = "quiet_enabled"
        private const val K_Q_START = "quiet_start"
        private const val K_Q_END = "quiet_end"
        private const val K_SUPPRESSED = "suppressed"
        private const val K_SIGNAL_PREFIX = "sig_"
    }
}
