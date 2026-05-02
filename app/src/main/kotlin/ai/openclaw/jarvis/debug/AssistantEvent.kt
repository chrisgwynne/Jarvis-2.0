package ai.openclaw.jarvis.debug

import ai.openclaw.jarvis.statemachine.AssistantState

data class AssistantEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val stateFrom: AssistantState,
    val stateTo: AssistantState,
    val action: String? = null,
    val error: String? = null,
) {
    val isError: Boolean get() = error != null
}
