package com.jarvis.githubissues.integration

import com.jarvis.githubissues.GitHubIssueLogger
import com.jarvis.githubissues.model.IssueContext
import com.jarvis.githubissues.model.IssueEvent

/**
 * STT/TTS/Bluetooth/wake-word hook. Single STT failures are very common and
 * not interesting; only repeated, consecutive failures within a short window
 * become issues. The threshold and window are configurable.
 */
class VoicePipelineHook(
    private val logger: GitHubIssueLogger,
    private val sttRepeatThreshold: Int = 3,
    private val sttWindowMillis: Long = 60_000L,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val recentSttFailures = ArrayDeque<Long>()

    fun onSttFailure(detail: String?, context: IssueContext) {
        val ts = now()
        recentSttFailures.addLast(ts)
        while (recentSttFailures.isNotEmpty() && ts - recentSttFailures.first() > sttWindowMillis) {
            recentSttFailures.removeFirst()
        }
        if (recentSttFailures.size >= sttRepeatThreshold) {
            logger.onVoiceFailure(IssueEvent.VoiceFailure.Mode.STT_REPEATED_FAIL, detail, context)
            recentSttFailures.clear() // reset; the deduper will block follow-ups
        }
    }

    fun onTtsFailure(detail: String?, context: IssueContext) =
        logger.onVoiceFailure(IssueEvent.VoiceFailure.Mode.TTS_FAIL, detail, context)

    fun onBluetoothRouteFailure(detail: String?, context: IssueContext) =
        logger.onVoiceFailure(IssueEvent.VoiceFailure.Mode.BLUETOOTH_ROUTE_FAIL, detail, context)

    fun onWakeFalseTriggerPattern(detail: String?, context: IssueContext) =
        logger.onVoiceFailure(IssueEvent.VoiceFailure.Mode.WAKE_FALSE_TRIGGER_PATTERN, detail, context)

    fun onEmptyTranscript(detail: String?, context: IssueContext) =
        logger.onVoiceFailure(IssueEvent.VoiceFailure.Mode.EMPTY_TRANSCRIPT, detail, context)
}
