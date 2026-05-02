package ai.openclaw.jarvis.screen.ui

import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettings
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsRepository
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ScreenAwarenessSettingsViewModel @Inject constructor(
    private val repo: ScreenAwarenessSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<ScreenAwarenessSettings> = repo.settings

    fun setEnabled(v: Boolean) = repo.update { it.copy(enabled = v) }
    fun setScreenshotAuto(v: Boolean) = repo.update { it.copy(screenshotAutoAnalyse = v) }
    fun setStoreScreenshots(v: Boolean) = repo.update { it.copy(storeScreenshots = v) }
    fun setVoicePrompt(v: Boolean) = repo.update { it.copy(voicePromptOnAnalysis = v) }
    fun setRetentionHours(v: Int) = repo.update { it.copy(retentionHours = v) }
    fun addToWhitelist(pkg: String) = repo.addToWhitelist(pkg)
    fun removeFromWhitelist(pkg: String) = repo.removeFromWhitelist(pkg)
    fun addToBlacklist(pkg: String) = repo.addToBlacklist(pkg)
    fun removeFromBlacklist(pkg: String) = repo.removeFromBlacklist(pkg)
}
