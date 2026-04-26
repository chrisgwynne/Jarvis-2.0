package ai.openclaw.jarvis.proactive.ui

import ai.openclaw.jarvis.proactive.SuggestionManager
import ai.openclaw.jarvis.proactive.model.Suggestion
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view-model for the active proactive suggestion chip. Surfaces
 * the manager's [SuggestionManager.active] flow and exposes accept /
 * dismiss callbacks.
 */
@HiltViewModel
class ProactiveSuggestionViewModel @Inject constructor(
    private val manager: SuggestionManager,
) : ViewModel() {

    val active: StateFlow<Suggestion?> = manager.active

    fun accept(suggestion: Suggestion) = manager.accept(suggestion)
    fun dismiss(suggestion: Suggestion, dontSuggestAgain: Boolean = false) =
        manager.dismiss(suggestion, dontSuggestAgain)
}
