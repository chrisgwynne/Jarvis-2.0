package ai.openclaw.jarvis.ui.viewmodel

import ai.openclaw.jarvis.protocol.ProtocolVersion
import ai.openclaw.jarvis.protocol.debug.ProtocolDebugBus
import ai.openclaw.jarvis.protocol.model.JarvisActionResult
import ai.openclaw.jarvis.protocol.model.JarvisLiveRequest
import ai.openclaw.jarvis.protocol.model.OpenClawResponse
import ai.openclaw.jarvis.protocol.model.OpenClawSkillManifest
import ai.openclaw.jarvis.protocol.validation.ProtocolValidator
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view-model for the protocol debug screen. Surfaces the
 * latest typed payloads from [ProtocolDebugBus] plus a pretty-print
 * helper so the UI can copy/export the JSON exactly as it was sent.
 */
@HiltViewModel
class ProtocolDebugViewModel @Inject constructor(
    bus: ProtocolDebugBus,
    private val validator: ProtocolValidator,
) : ViewModel() {

    val protocolVersion: String = ProtocolVersion.CURRENT
    val lastRequest: StateFlow<JarvisLiveRequest?> = bus.lastRequest
    val lastResponse: StateFlow<OpenClawResponse?> = bus.lastResponse
    val lastActionResult: StateFlow<JarvisActionResult?> = bus.lastActionResult
    val lastSkillManifest: StateFlow<OpenClawSkillManifest?> = bus.lastSkillManifest

    fun encode(req: JarvisLiveRequest?): String =
        req?.let { validator.json.encodeToString(JarvisLiveRequest.serializer(), it) } ?: "—"

    fun encode(resp: OpenClawResponse?): String =
        resp?.let { validator.json.encodeToString(OpenClawResponse.serializer(), it) } ?: "—"

    fun encode(result: JarvisActionResult?): String =
        result?.let { validator.json.encodeToString(JarvisActionResult.serializer(), it) } ?: "—"

    fun encode(manifest: OpenClawSkillManifest?): String =
        manifest?.let { validator.json.encodeToString(OpenClawSkillManifest.serializer(), it) } ?: "—"
}
