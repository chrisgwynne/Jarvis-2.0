package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import ai.openclaw.jarvis.screen.model.ScreenshotCaptured
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single in-process bus for screen-awareness events. Producers
 * ([ForegroundAppTracker], [service.ScreenAwarenessService],
 * [ScreenshotObserver]) publish here; consumers
 * ([PassiveAssistManager], the interpreter) subscribe.
 *
 * Using a SharedFlow with DROP_OLDEST keeps memory pressure low when
 * the user rapidly app-switches and nobody's listening yet.
 */
@Singleton
class ScreenContextBus @Inject constructor() {

    private val _events = MutableSharedFlow<ScreenContextEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ScreenContextEvent> = _events.asSharedFlow()

    private val _screenshots = MutableSharedFlow<ScreenshotCaptured>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val screenshots: SharedFlow<ScreenshotCaptured> = _screenshots.asSharedFlow()

    /** Latest event, exposed for the awareness UI. */
    private val _latest = MutableStateFlow<ScreenContextEvent?>(null)
    val latest: StateFlow<ScreenContextEvent?> = _latest.asStateFlow()

    fun publish(event: ScreenContextEvent) {
        _events.tryEmit(event)
        _latest.value = event
    }

    fun publishScreenshot(shot: ScreenshotCaptured) {
        _screenshots.tryEmit(shot)
    }
}
