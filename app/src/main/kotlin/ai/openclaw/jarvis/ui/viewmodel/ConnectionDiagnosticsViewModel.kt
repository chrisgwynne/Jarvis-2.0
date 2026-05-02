package ai.openclaw.jarvis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ai.openclaw.jarvis.data.local.PairingStore
import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.network.ConnectionFailure
import ai.openclaw.jarvis.network.DiagnosticEvent
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.network.OpenClawConnectionTester
import ai.openclaw.jarvis.network.TestStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionDiagnosticsViewModel @Inject constructor(
    private val client: OpenClawClient,
    private val tester: OpenClawConnectionTester,
    private val settingsStore: SettingsDataStore,
    private val pairingStore: PairingStore,
) : ViewModel() {

    val gatewayState = client.gatewayState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), client.gatewayState.value)

    val lastFailure: StateFlow<ConnectionFailure?> = client.lastFailure
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val diagLog: StateFlow<List<DiagnosticEvent>> = client.diagLog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val normalizedUrl: StateFlow<String> = client.normalizedUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val settings = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            ai.openclaw.jarvis.data.local.JarvisSettings())

    val deviceId: String get() = pairingStore.getDeviceId()
    val nodeId: String? get() = pairingStore.getNodeId()
    val isPaired: Boolean get() = pairingStore.isPaired()
    val isApproved: Boolean get() = pairingStore.isApproved()
    val pairRequestId: String? get() = pairingStore.getPairRequestId()
    val pairingToken: String? get() = pairingStore.getPairingToken()

    // ─── Connection test ──────────────────────────────────────────────────────

    sealed class TestStatus {
        object Idle : TestStatus()
        object Running : TestStatus()
        data class Done(val steps: List<TestStep>, val workingPath: String?) : TestStatus()
    }

    private val _testStatus = MutableStateFlow<TestStatus>(TestStatus.Idle)
    val testStatus: StateFlow<TestStatus> = _testStatus.asStateFlow()

    fun runConnectionTest(url: String) {
        if (_testStatus.value is TestStatus.Running) return
        _testStatus.value = TestStatus.Running
        viewModelScope.launch {
            tester.runTest(url) { steps ->
                _testStatus.value = TestStatus.Done(steps, null)
            }.also { result ->
                _testStatus.value = TestStatus.Done(result.steps, result.workingWsPath)
            }
        }
    }

    fun resetTest() {
        _testStatus.value = TestStatus.Idle
    }

    fun retryConnection() {
        client.disconnect()
        client.connect()
    }
}
