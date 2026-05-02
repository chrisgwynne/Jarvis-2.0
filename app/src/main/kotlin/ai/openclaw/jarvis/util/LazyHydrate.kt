package ai.openclaw.jarvis.util

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Hydrate a [MutableStateFlow] in the background after the singleton's
 * constructor returns, instead of doing the disk read synchronously in
 * the constructor (which blocks whatever thread first injected the
 * singleton — typically `JarvisApp.onCreate`, i.e. the main thread).
 *
 * Concurrency contract:
 *   - `flow` is set ONLY if no `markUpdated()` has happened first.
 *   - Callers that mutate the flow must call [markUpdated] before they
 *     write so an in-flight hydrate can't clobber their value.
 *
 * Returns a small handle the caller stores; cancel via [cancel] if the
 * owner is ever torn down (singletons aren't, so this is mostly future-
 * proofing).
 */
class LazyHydrate<T>(
    private val flow: MutableStateFlow<T>,
    private val load: () -> T,
) {
    private val updated = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            val loaded = load()
            // CAS — if a manual update beat us to it, drop the loaded value.
            if (updated.compareAndSet(false, true)) {
                flow.value = loaded
            }
        }
    }

    /** Mark the flow as user-updated so any pending hydrate is dropped. */
    fun markUpdated() {
        updated.set(true)
    }

    fun cancel() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
