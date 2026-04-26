package ai.openclaw.jarvis.screen

import ai.openclaw.jarvis.screen.model.ScreenshotCaptured

/**
 * Seam over the OpenClaw-side screenshot analyser. Production binding
 * is [ScreenshotAutoAnalyser]; tests bind a no-op so they don't have
 * to construct the protocol client.
 */
interface ScreenshotAnalyser {
    fun analyse(shot: ScreenshotCaptured)

    object NoOp : ScreenshotAnalyser {
        override fun analyse(shot: ScreenshotCaptured) {}
    }
}
