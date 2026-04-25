package ai.openclaw.jarvis.session

import ai.openclaw.jarvis.statemachine.ErrorCode
import ai.openclaw.jarvis.statemachine.ErrorRecoveryManager
import ai.openclaw.jarvis.statemachine.FallbackAction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests offline-queue-related logic via ErrorRecoveryManager strategies.
 * Full offline queue persistence tests require an Android context and live in androidTest.
 */
class OfflineQueueLogicTest {

    private lateinit var recovery: ErrorRecoveryManager

    @Before
    fun setUp() {
        recovery = ErrorRecoveryManager()
    }

    @Test
    fun `GATEWAY_OFFLINE strategy recommends QUEUE_OFFLINE fallback`() {
        val strategy = recovery.strategyFor(ErrorCode.GATEWAY_OFFLINE)
        assertEquals(FallbackAction.QUEUE_OFFLINE, strategy.fallbackAction)
        assertTrue(strategy.userMessage.isNotBlank())
    }

    @Test
    fun `STT_FAILURE strategy recommends RETRY_STT fallback`() {
        val strategy = recovery.strategyFor(ErrorCode.STT_FAILURE)
        assertEquals(FallbackAction.RETRY_STT, strategy.fallbackAction)
        assertTrue(strategy.shouldRetry)
    }

    @Test
    fun `PERMISSION_MISSING strategy recommends REQUEST_PERMISSION fallback`() {
        val strategy = recovery.strategyFor(ErrorCode.PERMISSION_MISSING)
        assertEquals(FallbackAction.REQUEST_PERMISSION, strategy.fallbackAction)
    }

    @Test
    fun `MALFORMED_RESPONSE strategy returns to idle`() {
        val strategy = recovery.strategyFor(ErrorCode.MALFORMED_RESPONSE)
        assertEquals(FallbackAction.RETURN_TO_IDLE, strategy.fallbackAction)
    }

    @Test
    fun `UNKNOWN error code still returns a strategy with a message`() {
        val strategy = recovery.strategyFor(ErrorCode.UNKNOWN)
        assertTrue(strategy.userMessage.isNotBlank())
        assertEquals(FallbackAction.RETURN_TO_IDLE, strategy.fallbackAction)
    }

    @Test
    fun `UNKNOWN error code with detail uses custom message`() {
        val strategy = recovery.strategyFor(ErrorCode.UNKNOWN, "disk full")
        assertTrue(strategy.userMessage.contains("disk full"))
    }

    @Test
    fun `stt error code 5 maps to STT_FAILURE`() {
        assertEquals(ErrorCode.STT_FAILURE, recovery.errorCodeFromSttCode(5))
    }

    @Test
    fun `stt error code 3 maps to GATEWAY_OFFLINE`() {
        assertEquals(ErrorCode.GATEWAY_OFFLINE, recovery.errorCodeFromSttCode(3))
    }

    @Test
    fun `IDENTITY_LOW_CONFIDENCE strategy does not speak`() {
        val strategy = recovery.strategyFor(ErrorCode.IDENTITY_LOW_CONFIDENCE)
        assertFalse(strategy.shouldSpeak)
    }

    @Test
    fun `CONTACT_AMBIGUOUS strategy uses detail message when provided`() {
        val strategy = recovery.strategyFor(ErrorCode.CONTACT_AMBIGUOUS, "Found: Chris Smith, Chris Jones")
        assertTrue(strategy.userMessage.contains("Chris Smith"))
    }
}
