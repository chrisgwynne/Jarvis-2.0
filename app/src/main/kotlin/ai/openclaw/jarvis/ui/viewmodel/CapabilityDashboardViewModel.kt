package ai.openclaw.jarvis.ui.viewmodel

import ai.openclaw.jarvis.awareness.AwarenessSnapshot
import ai.openclaw.jarvis.awareness.CapabilityAwarenessManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pulls a fresh [AwarenessSnapshot] from [CapabilityAwarenessManager]
 * each time [refresh] is called (or on first observation).
 *
 * The snapshot is cheap to compute and there's no underlying flow to
 * subscribe to, so the UI uses a manual refresh + lifecycle-tied
 * initial pull rather than a StateFlow over a query.
 */
@HiltViewModel
class CapabilityDashboardViewModel @Inject constructor(
    private val manager: CapabilityAwarenessManager,
) : ViewModel() {

    private val _snapshot = MutableStateFlow(manager.snapshot())
    val snapshot: StateFlow<AwarenessSnapshot> = _snapshot.asStateFlow()

    fun refresh() {
        _snapshot.value = manager.snapshot()
    }
}
