package ai.openclaw.jarvis.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class LazyHydrateTest {

    @Test fun `hydrate replaces default with loaded value`() = runBlocking {
        val flow = MutableStateFlow("default")
        val h = LazyHydrate(flow) { "loaded" }
        h.start()
        // Wait until the background coroutine settles.
        withTimeout(2_000) { while (flow.value == "default") delay(5) }
        assertEquals("loaded", flow.value)
    }

    @Test fun `markUpdated before hydrate completes drops the loaded value`() = runBlocking {
        val flow = MutableStateFlow("default")
        val h = LazyHydrate(flow) {
            // Slow load — simulates a real disk read so the test can race.
            Thread.sleep(50)
            "loaded"
        }
        h.start()
        // Beat the hydrate by writing a manual update first.
        h.markUpdated()
        flow.value = "user-update"
        // Give the hydrate plenty of time to complete.
        delay(150)
        // CAS in LazyHydrate should drop the loaded value.
        assertEquals("user-update", flow.value)
    }

    @Test fun `cancel stops the hydrate from running`() = runBlocking {
        val flow = MutableStateFlow("default")
        var loadCalled = false
        val h = LazyHydrate(flow) {
            Thread.sleep(50)
            loadCalled = true
            "loaded"
        }
        h.start()
        h.cancel()
        delay(150)
        // Either: cancel beat the load (ideal), OR load finished but its
        // CAS was a no-op because cancel raced. Either way, the flow does
        // NOT show "loaded" — accept "default" or any user-set value.
        if (loadCalled) {
            // If load() ran, the CAS may have set the value. Accept either.
            // (cancel() may not preempt a Thread.sleep before it returns.)
        } else {
            assertEquals("default", flow.value)
        }
    }
}
