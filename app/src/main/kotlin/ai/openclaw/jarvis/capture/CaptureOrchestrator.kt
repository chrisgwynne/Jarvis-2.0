package ai.openclaw.jarvis.capture

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [ai.openclaw.jarvis.executor.AndroidActionExecutor] (which runs in a
 * coroutine on the main thread) and [ai.openclaw.jarvis.MainActivity] (which owns the
 * Activity-level [androidx.activity.result.ActivityResultLauncher] instances needed to
 * launch the camera and the MediaProjection permission dialog).
 *
 * The executor suspends on [requestCamera] / [requestScreenshot], emitting a typed
 * [Request] onto [requests]. MainActivity collects those requests, performs the
 * Activity-level work, and completes the deferred to unblock the executor.
 */
@Singleton
class CaptureOrchestrator @Inject constructor() {

    sealed class Request {
        /** Launch the system camera targeting front (selfie) or rear lens. */
        data class Camera(
            val selfie: Boolean,
            val deferred: CompletableDeferred<Result>,
        ) : Request()

        /** Capture the current screen via MediaProjection. */
        data class Screenshot(
            val deferred: CompletableDeferred<Result>,
        ) : Request()
    }

    data class Result(val saved: Boolean, val message: String)

    private val _requests = MutableSharedFlow<Request>(extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()

    /** Called by the executor; suspends until MainActivity delivers the result (60 s timeout). */
    suspend fun requestCamera(selfie: Boolean): Result {
        val deferred = CompletableDeferred<Result>()
        _requests.emit(Request.Camera(selfie, deferred))
        return withTimeoutOrNull(60_000) { deferred.await() }
            ?: Result(false, "Camera timed out or no activity to handle it.")
    }

    /** Called by the executor; suspends until MainActivity delivers the result (30 s timeout). */
    suspend fun requestScreenshot(): Result {
        val deferred = CompletableDeferred<Result>()
        _requests.emit(Request.Screenshot(deferred))
        return withTimeoutOrNull(30_000) { deferred.await() }
            ?: Result(false, "Screenshot timed out or no activity to handle it.")
    }
}
