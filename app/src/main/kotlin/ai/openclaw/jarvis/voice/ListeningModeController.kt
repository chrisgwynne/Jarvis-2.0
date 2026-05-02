package ai.openclaw.jarvis.voice

import ai.openclaw.jarvis.data.local.ListeningModeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningModeController @Inject constructor(
    private val store: ListeningModeStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _mode = MutableStateFlow<ListeningMode>(ListeningMode.Active)
    val mode: StateFlow<ListeningMode> = _mode.asStateFlow()

    private var resumeJob: Job? = null

    init {
        scope.launch {
            val saved = store.load()
            _mode.value = saved
            if (saved is ListeningMode.Paused) {
                val remaining = saved.resumeAt - System.currentTimeMillis()
                if (remaining > 0) scheduleResume(remaining) else setActive()
            }
        }
    }

    fun setActive() {
        resumeJob?.cancel()
        update(ListeningMode.Active)
    }

    fun setSilent() {
        resumeJob?.cancel()
        update(ListeningMode.Silent)
    }

    fun setPaused(durationMs: Long) {
        resumeJob?.cancel()
        val mode = ListeningMode.Paused(System.currentTimeMillis() + durationMs)
        update(mode)
        scheduleResume(durationMs)
    }

    fun setStopped() {
        resumeJob?.cancel()
        update(ListeningMode.Stopped)
    }

    private fun update(mode: ListeningMode) {
        _mode.value = mode
        scope.launch { store.save(mode) }
    }

    private fun scheduleResume(delayMs: Long) {
        resumeJob = scope.launch {
            delay(delayMs)
            setActive()
        }
    }
}
