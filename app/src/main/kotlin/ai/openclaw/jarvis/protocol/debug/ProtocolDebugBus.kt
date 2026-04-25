package ai.openclaw.jarvis.protocol.debug

import ai.openclaw.jarvis.protocol.model.JarvisActionResult
import ai.openclaw.jarvis.protocol.model.JarvisLiveRequest
import ai.openclaw.jarvis.protocol.model.OpenClawResponse
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifest
import ai.openclaw.jarvis.protocol.validation.ProtocolError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single in-memory bus for the protocol debug screen.
 *
 * Holds the most recent typed request / response / action result and
 * skill manifest so the user can inspect what's actually crossing the
 * wire. No persistence — debug only, cleared on app restart.
 */
@Singleton
class ProtocolDebugBus @Inject constructor() {
    val lastRequest: MutableStateFlow<JarvisLiveRequest?> = MutableStateFlow(null)
    val lastResponse: MutableStateFlow<OpenClawResponse?> = MutableStateFlow(null)
    val lastActionResult: MutableStateFlow<JarvisActionResult?> = MutableStateFlow(null)
    val lastSkillManifest: MutableStateFlow<OpenClawSkillManifest?> = MutableStateFlow(null)

    private val _malformed = MutableSharedFlow<ProtocolError>(replay = 8, extraBufferCapacity = 16)
    val malformed: SharedFlow<ProtocolError> = _malformed.asSharedFlow()

    fun pushMalformed(error: ProtocolError) { _malformed.tryEmit(error) }
}
