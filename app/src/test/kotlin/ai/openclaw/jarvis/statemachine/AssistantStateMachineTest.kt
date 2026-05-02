package ai.openclaw.jarvis.statemachine

import ai.openclaw.jarvis.debug.AssistantEventLog
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AssistantStateMachineTest {

    private lateinit var machine: AssistantStateMachine
    private lateinit var log: AssistantEventLog

    @Before
    fun setUp() {
        log     = AssistantEventLog()
        machine = AssistantStateMachine(log)
    }

    @Test
    fun `initial state is DISABLED`() {
        assertEquals(AssistantState.DISABLED, machine.currentState)
    }

    @Test
    fun `valid transition DISABLED to IDLE_LISTENING succeeds`() {
        assertTrue(machine.transition(AssistantState.IDLE_LISTENING))
        assertEquals(AssistantState.IDLE_LISTENING, machine.currentState)
    }

    @Test
    fun `invalid transition DISABLED to SPEAKING is rejected`() {
        val result = machine.transition(AssistantState.SPEAKING)
        assertFalse(result)
        assertEquals(AssistantState.DISABLED, machine.currentState)
    }

    @Test
    fun `full happy path transitions succeed`() {
        machine.transition(AssistantState.IDLE_LISTENING)
        machine.transition(AssistantState.WAKE_DETECTED)
        machine.transition(AssistantState.CAPTURING_COMMAND)
        machine.transition(AssistantState.TRANSCRIBING)
        machine.transition(AssistantState.ROUTING)
        machine.transition(AssistantState.EXECUTING_ANDROID)
        machine.transition(AssistantState.SPEAKING)
        machine.transition(AssistantState.RETURNING_TO_LISTENING)
        assertTrue(machine.transition(AssistantState.IDLE_LISTENING))
    }

    @Test
    fun `interrupt from CAPTURING_COMMAND returns to IDLE_LISTENING`() {
        machine.transition(AssistantState.IDLE_LISTENING)
        machine.transition(AssistantState.CAPTURING_COMMAND)
        assertTrue(machine.interrupt("test cancel"))
        assertEquals(AssistantState.IDLE_LISTENING, machine.currentState)
    }

    @Test
    fun `interrupt from IDLE_LISTENING is a no-op`() {
        machine.transition(AssistantState.IDLE_LISTENING)
        assertFalse(machine.interrupt())
        assertEquals(AssistantState.IDLE_LISTENING, machine.currentState)
    }

    @Test
    fun `interrupt from WAITING_OPENCLAW returns to IDLE_LISTENING`() {
        machine.transition(AssistantState.IDLE_LISTENING)
        machine.transition(AssistantState.WAKE_DETECTED)
        machine.transition(AssistantState.CAPTURING_COMMAND)
        machine.transition(AssistantState.TRANSCRIBING)
        machine.transition(AssistantState.ROUTING)
        machine.transition(AssistantState.WAITING_OPENCLAW)
        assertTrue(machine.interrupt("cancel"))
        assertEquals(AssistantState.IDLE_LISTENING, machine.currentState)
    }

    @Test
    fun `invalid transitions are logged as errors`() {
        machine.transition(AssistantState.SPEAKING) // invalid from DISABLED
        val errorEvent = log.latestError()
        assertNotNull(errorEvent)
        assertTrue(errorEvent!!.error!!.contains("INVALID"))
    }

    @Test
    fun `isActive returns false when IDLE`() {
        machine.transition(AssistantState.IDLE_LISTENING)
        assertFalse(machine.isActive())
    }

    @Test
    fun `isActive returns true when CAPTURING_COMMAND`() {
        machine.transition(AssistantState.IDLE_LISTENING)
        machine.transition(AssistantState.WAKE_DETECTED)
        machine.transition(AssistantState.CAPTURING_COMMAND)
        assertTrue(machine.isActive())
    }
}
