package ai.openclaw.jarvis.githubissues.integration

import ai.openclaw.jarvis.githubissues.detect.UserCorrectionDetector
import ai.openclaw.jarvis.statemachine.AssistantState
import ai.openclaw.jarvis.statemachine.AssistantStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    @Suppress("unused") private val correctionDetector: UserCorrectionDetector, // eager singleton
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    }
}
