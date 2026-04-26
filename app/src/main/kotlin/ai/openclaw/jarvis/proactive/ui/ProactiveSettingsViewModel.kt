package ai.openclaw.jarvis.proactive.ui

import ai.openclaw.jarvis.proactive.model.Aggressiveness
import ai.openclaw.jarvis.proactive.model.ProactiveSettings
import ai.openclaw.jarvis.proactive.model.QuietHours
import ai.openclaw.jarvis.proactive.model.SignalType
import ai.openclaw.jarvis.proactive.store.ProactiveSettingsRepository
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ProactiveSettingsViewModel @Inject constructor(
    private val repo: ProactiveSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<ProactiveSettings> = repo.settings

    fun setEnabled(v: Boolean) = repo.update { it.copy(enabled = v) }
    fun setAggressiveness(a: Aggressiveness) = repo.update { it.copy(aggressiveness = a) }
    fun setQuietHours(q: QuietHours) = repo.update { it.copy(quietHours = q) }
    fun setSignalEnabled(sig: SignalType, enabled: Boolean) = repo.update {
        it.copy(perSignal = it.perSignal + (sig to enabled))
    }
    fun unsuppressAll() = repo.unsuppressAll()
}
