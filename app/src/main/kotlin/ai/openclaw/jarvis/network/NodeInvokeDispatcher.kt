package ai.openclaw.jarvis.network

import ai.openclaw.jarvis.data.models.NodeInvokeFrame
import ai.openclaw.jarvis.data.models.NodeInvokeResultFrame
import ai.openclaw.jarvis.executor.AndroidActionExecutor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Subscribes to [GatewayEvent.InvokeCommand] frames pushed by OpenClaw,
 * dispatches them to the legacy [AndroidActionExecutor.execute] string
 * entry point, and sends a `node.invoke.result` frame back so OpenClaw
 * can correlate the outcome.
 *
 * Why the legacy entry point and not the typed contract path: OpenClaw's
 * `node.invoke` is the *legacy* invocation channel — it pre-dates the
 * typed `OpenClawAction` contract. The typed path runs through
 * [ai.openclaw.jarvis.protocol.executor.ContractActionExecutor] +
 * [ai.openclaw.jarvis.policy.engine.AutonomyPolicyEngine] already.
 *
 * Without this subscriber the frame was silently dropped on the floor —
 * OpenClaw would wait forever for the matching `node.invoke.result`.
 */
@Singleton
class NodeInvokeDispatcher @Inject constructor(
    private val client: OpenClawClient,
    private val executor: AndroidActionExecutor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    /** Idempotent. Starts subscribing to `node.invoke` frames. */
    fun start() {
        if (running) return
        running = true
        client.events
            .filterIsInstance<GatewayEvent.InvokeCommand>()
            .onEach { evt -> scope.launch { handle(evt.frame) } }
            .launchIn(scope)
    }

    private suspend fun handle(frame: NodeInvokeFrame) {
        val params = paramsToStringMap(frame.params)
        val outcome = runCatching { executor.execute(frame.action, params) }.getOrNull()
        val result = if (outcome?.success == true) {
            NodeInvokeResultFrame(
                correlationId = frame.correlationId,
                status = "success",
                result = buildJsonObject {
                    put("spokenReply", JsonPrimitive(outcome.spokenReply))
                },
                error = null,
            )
        } else {
            NodeInvokeResultFrame(
                correlationId = frame.correlationId,
                status = "error",
                result = null,
                error = outcome?.error ?: "execution_failed",
            )
        }
        client.sendInvokeResult(result)
    }

    /** Flatten the JSON params object into the legacy String map shape
     *  the existing executor expects. Anything non-primitive is dropped. */
    private fun paramsToStringMap(obj: JsonObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        for ((k, v) in obj) {
            runCatching { v.jsonPrimitive.content }.getOrNull()?.let { out[k] = it }
        }
        return out
    }
}
