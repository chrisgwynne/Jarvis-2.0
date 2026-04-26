package ai.openclaw.jarvis.screen.integration

import ai.openclaw.jarvis.data.local.SettingsDataStore
import ai.openclaw.jarvis.protocol.client.OpenClawProtocolClient
import ai.openclaw.jarvis.protocol.model.JarvisSessionEvent
import ai.openclaw.jarvis.protocol.util.IsoTimestamp
import ai.openclaw.jarvis.screen.ContextInterpreter
import ai.openclaw.jarvis.screen.ScreenContextBus
import ai.openclaw.jarvis.screen.model.ScreenContextEvent
import ai.openclaw.jarvis.screen.store.ScreenAwarenessSettingsSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Periodically forwards screen context to OpenClaw as
 * `jarvis.screen_context` JarvisSessionEvents. The body is the
 * structured object the spec asks for:
 *
 *     { app, visibleText, pageType, url, screenshot? }
 *
 * Sensitive / blacklisted apps never reach this class because the
 * upstream [ScreenContextBus] producers have already filtered them.
 *
 * Throttled with [MIN_INTERVAL_MS] so we don't flood the WebSocket
 * during rapid app switching.
 */
@Singleton
class ScreenContextLogger @Inject constructor(
    private val bus: ScreenContextBus,
    private val interpreter: ContextInterpreter,
    private val protocolClient: OpenClawProtocolClient,
    private val settings: SettingsDataStore,
    private val settingsSource: ScreenAwarenessSettingsSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastSentAt: Long = 0L
    @Volatile private var lastSentPackage: String? = null

    @Volatile private var running = false
    fun start() {
        if (running) return
        running = true
        bus.events.onEach(::maybeSend).launchIn(scope)
    }

    private suspend fun maybeSend(event: ScreenContextEvent) {
        if (!settingsSource.current().enabled) return
        val now = System.currentTimeMillis()
        if (event.packageName == lastSentPackage && now - lastSentAt < MIN_INTERVAL_MS) return
        lastSentPackage = event.packageName
        lastSentAt = now

        val ctx = interpreter.interpret(event)
        val sessionKey = runCatching { settings.settings.first().sessionKey }
            .getOrDefault("jarvis:user:android")
        val body = buildJsonObject {
            put("app", JsonPrimitive(ctx.appLabel))
            put("packageName", JsonPrimitive(ctx.packageName))
            put("category", JsonPrimitive(ctx.category.name))
            put("pageType", JsonPrimitive(ctx.pageType.name))
            ctx.url?.let { put("url", JsonPrimitive(it)) }
            ctx.pageTitle?.let { put("pageTitle", JsonPrimitive(it)) }
            event.extractedText?.let { put("visibleText", JsonPrimitive(it)) }
        }
        scope.launch {
            protocolClient.sendSessionEvent(
                JarvisSessionEvent(
                    requestId = java.util.UUID.randomUUID().toString(),
                    sessionKey = sessionKey,
                    timestamp = IsoTimestamp.now(),
                    name = "jarvis.screen_context",
                    body = body,
                )
            )
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 5_000L
    }
}
