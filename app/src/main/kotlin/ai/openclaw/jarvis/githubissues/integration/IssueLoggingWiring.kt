package ai.openclaw.jarvis.githubissues.integration

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.detect.UserCorrectionDetector
import ai.openclaw.jarvis.githubissues.model.ErrorSnapshot
import ai.openclaw.jarvis.githubissues.model.IssueEvent
import ai.openclaw.jarvis.network.OpenClawClient
import ai.openclaw.jarvis.statemachine.AssistantState
import ai.openclaw.jarvis.statemachine.AssistantStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Boot-time wiring that subscribes the issue-logging hooks to flows
 * exposed by other Jarvis subsystems. Started once from
 * [ai.openclaw.jarvis.JarvisApp.onCreate] (via the queue worker, which
 * pulls this in transitively to ensure activation).
 *
 * The class itself is stateless aside from the subscription job, so
 * cancelling the scope cleanly shuts everything down on terminate.
 */
@Singleton
class IssueLoggingWiring @Inject constructor(
    private val stateMachine: AssistantStateMachine,
    private val stateMachineHook: StateMachineHook,
    private val contextBuilder: IssueContextBuilder,
    private val openClawClient: OpenClawClient,
    private val logger: GitHubIssueLogger,
    @Suppress("unused") private val correctionDetector: UserCorrectionDetector, // eager singleton
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tsFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun start() {
        stateMachine.transitions
            .filter { it.to == AssistantState.ERROR_RECOVERY }
            .onEach { t ->
                stateMachineHook.onTransition(
                    fromState = t.from.name,
                    toState = t.to.name,
                    triggerReason = t.reason,
                    context = contextBuilder.build(
                        previousState = t.from.name,
                        userCommand = null,
                    ),
                )
            }
            .launchIn(scope)

        // File a GitHub issue for every distinct gateway connection failure,
        // attaching the full diagnostic log so we can debug without a device.
        openClawClient.lastFailure
            .filterNotNull()
            .distinctUntilChangedBy { it.timestamp }
            .onEach { failure ->
                val diagLog = openClawClient.diagLog.value
                    .take(75)
                    .reversed() // stored newest-first; render oldest-first
                    .joinToString("\n") { e ->
                        "[${tsFormat.format(Date(e.timestamp))} ${e.level.name}] ${e.message}"
                    }
                val context = contextBuilder.build(
                    actualBehaviour = "Connection failed at stage '${failure.stage}': ${failure.message}",
                    error = ErrorSnapshot(
                        errorCode = failure.errorType,
                        message   = failure.message,
                        cause     = failure.reason,
                        stackTrace = diagLog,
                    ),
                )
                logger.onOpenClawFailure(
                    mode      = IssueEvent.OpenClawFailure.Mode.CONNECTION_FAILURE,
                    errorCode = failure.errorType,
                    message   = "${failure.stage}: ${failure.message}",
                    context   = context,
                )
            }
            .launchIn(scope)
    }
}
