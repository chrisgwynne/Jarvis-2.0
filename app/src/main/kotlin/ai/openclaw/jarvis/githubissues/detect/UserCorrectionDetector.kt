package ai.openclaw.jarvis.githubissues.detect

import ai.openclaw.jarvis.githubissues.GitHubIssueLogger
import ai.openclaw.jarvis.githubissues.model.IssueContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches transcripts for the canonical user-correction phrases listed in
 * the spec ("that's wrong", "you misunderstood", etc.) and, when one is
 * detected within a short window of the previous executed command, fires
 * a `user_correction` issue via [GitHubIssueLogger].
 *
 * The detector intentionally keeps a tiny in-memory record of the last
 * command — long-lived correlation belongs to the OpenClaw session log.
 */
@Singleton
class UserCorrectionDetector @Inject constructor(
    private val logger: GitHubIssueLogger,
) {
    private val correlationWindowMillis: Long = 30_000L
    private val now: () -> Long = System::currentTimeMillis
    private data class LastCommand(
        val command: String?,
        val route: String?,
        val result: String?,
        val timestampMillis: Long
    )

    @Volatile private var last: LastCommand? = null

    fun rememberLastCommand(command: String?, route: String?, result: String?) {
        last = LastCommand(command, route, result, now())
    }

    /**
     * Inspect a fresh transcript. If it matches a correction phrase and we
     * still have a recent command on file, fire the issue and return true.
     */
    fun maybeReport(transcript: String, context: IssueContext): Boolean {
        val phrase = matchedPhrase(transcript) ?: return false
        val ref = last
        if (ref == null || now() - ref.timestampMillis > correlationWindowMillis) {
            // Still report the correction itself; the user clearly
            // disagreed with something even if we can't tie it back.
            logger.onUserCorrection(
                correctionPhrase = phrase,
                previousCommand = null,
                previousRoute = null,
                previousResult = null,
                context = context
            )
            return true
        }
        logger.onUserCorrection(
            correctionPhrase = phrase,
            previousCommand = ref.command,
            previousRoute = ref.route,
            previousResult = ref.result,
            context = context
        )
        return true
    }

    private fun matchedPhrase(input: String): String? {
        val lower = input.trim().lowercase()
        if (lower.isEmpty()) return null
        return PHRASES.firstOrNull { lower.contains(it) }
    }

    companion object {
        // Lower-case substrings we look for. Order doesn't matter for
        // correctness; we just take the first match for the report.
        private val PHRASES = listOf(
            "that's wrong",
            "thats wrong",
            "you misunderstood",
            "not that",
            "that didn't work",
            "that didnt work",
            "you can't do that",
            "you cant do that",
            "why didn't you",
            "why didnt you",
            "you should have"
        )
    }
}
