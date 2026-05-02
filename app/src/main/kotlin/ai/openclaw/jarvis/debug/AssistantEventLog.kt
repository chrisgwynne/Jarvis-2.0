package ai.openclaw.jarvis.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_EVENTS = 200

@Singleton
class AssistantEventLog @Inject constructor() {
    private val _events = MutableStateFlow<List<AssistantEvent>>(emptyList())
    val events: StateFlow<List<AssistantEvent>> = _events.asStateFlow()

    fun log(event: AssistantEvent) {
        _events.value = (_events.value + event).takeLast(MAX_EVENTS)
    }

    fun clear() {
        _events.value = emptyList()
    }

    fun latestError(): AssistantEvent? = _events.value.lastOrNull { it.isError }
}
