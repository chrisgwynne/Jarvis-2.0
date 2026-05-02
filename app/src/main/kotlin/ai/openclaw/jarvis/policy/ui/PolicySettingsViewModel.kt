package ai.openclaw.jarvis.policy.ui

import ai.openclaw.jarvis.policy.model.ActionKind
import ai.openclaw.jarvis.policy.model.AutonomyLevel
import ai.openclaw.jarvis.policy.store.AutonomyMode
import ai.openclaw.jarvis.policy.store.PolicySettings
import ai.openclaw.jarvis.policy.store.PolicySettingsRepository
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PolicySettingsViewModel @Inject constructor(
    private val repo: PolicySettingsRepository,
) : ViewModel() {

    val settings: StateFlow<PolicySettings> = repo.settings

    fun setMode(mode: AutonomyMode) = repo.update { it.copy(mode = mode) }
    fun setRequireConfirmAllOutbound(v: Boolean) = repo.update { it.copy(requireConfirmAllOutbound = v) }
    fun setAllowAutoExecuteSafe(v: Boolean) = repo.update { it.copy(allowAutoExecuteSafe = v) }
    fun setQuietHoursForceConfirm(v: Boolean) = repo.update { it.copy(quietHoursForceConfirm = v) }
    fun setApprovalTimeout(min: Int) = repo.update { it.copy(approvalTimeoutMinutes = min) }
    fun setOverride(kind: ActionKind, level: AutonomyLevel?) = repo.setOverride(kind, level)
}
