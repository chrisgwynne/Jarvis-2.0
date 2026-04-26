package ai.openclaw.jarvis.proactive.store

import ai.openclaw.jarvis.proactive.model.ProactiveSettings

/**
 * Read-only seam over the persisted proactive settings + suppress-set
 * mutator. Pulled into its own interface so the SuggestionManager can be
 * unit-tested without bringing in the Hilt repository (and Android
 * SharedPreferences along with it).
 */
interface ProactiveSettingsSource {
    fun current(): ProactiveSettings
    fun suppress(suggestionId: String)
}
