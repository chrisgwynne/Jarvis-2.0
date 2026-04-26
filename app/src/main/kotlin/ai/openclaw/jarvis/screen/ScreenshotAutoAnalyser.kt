package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.data.models.GatewayState
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient
import ai.openclaw.jarvis.protocol.model.JarvisSessionEvent
import ai.openclaw.jarvis.protocol.util.IsoTimestamp
import ai.openclaw.jarvis.screen.model.ScreenshotCaptured
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * "Look at this" without saying it. When [analyse] is called we emit a
 * `jarvis.screen_screenshot_captured` JarvisSessionEvent to OpenClaw
 * carrying the screenshot URI and a tiny context blob — OpenClaw can
 * pull the actual image out-of-band if the user has opted in to image
 * sharing.
 *
 * No-op when OpenClaw is offline (the suggestion chip still surfaces
 * locally so the user can act on it later).
 */
@Singleton
class ScreenshotAutoAnalyser @Inject constructor(
    private val openClawClient: OpenClawClient,
    private val protocolClient: OpenClawProtocolClient,
    private val settings: SettingsDataStore,
    private val bus: ScreenContextBus,
) : ScreenshotAnalyser {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun analyse(shot: ScreenshotCaptured) {
        scope.launch {
            if (openClawClient.gatewayState.value != GatewayState.CONNECTED) return@launch
            val sessionKey = runCatching { settings.settings.first().sessionKey }
                .getOrDefault("jarvis:user:android")
            val foreground = bus.latest.value
            val payload = buildJsonObject {
                put("uri", JsonPrimitive(shot.uri))
                put("timestamp", JsonPrimitive(shot.timestampMillis))
                put("source", JsonPrimitive(shot.source.name))
                foreground?.let {
                    put("foregroundApp", JsonPrimitive(it.packageName))
                    put("foregroundCategory", JsonPrimitive(it.category.name))
                    it.url?.let { u -> put("foregroundUrl", JsonPrimitive(u)) }
                    it.pageTitle?.let { t -> put("foregroundTitle", JsonPrimitive(t)) }
                }
            }
            protocolClient.sendSessionEvent(
                JarvisSessionEvent(
                    requestId = java.util.UUID.randomUUID().toString(),
                    sessionKey = sessionKey,
                    timestamp = IsoTimestamp.now(),
                    name = "jarvis.screen_screenshot_captured",
                    body = payload,
                )
            )
        }
    }
}
