package ai.openclaw.jarvis.policy.ui

import ai.openclaw.jarvis.policy.ApprovalCoordinator
import ai.openclaw.jarvis.policy.store.PendingApproval
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PendingApprovalsViewModel @Inject constructor(
    private val coordinator: ApprovalCoordinator,
) : ViewModel() {

    val approvals: StateFlow<List<PendingApproval>> = coordinator.live

    fun approve(id: String) = viewModelScope.launch { coordinator.approve(id) }
    fun reject(id: String) = coordinator.reject(id)
}
