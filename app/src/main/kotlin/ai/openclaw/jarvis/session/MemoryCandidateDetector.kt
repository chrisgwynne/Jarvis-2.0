package ai.openclaw.jarvis.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines whether an utterance deserves to be flagged as a memory candidate
 * for OpenClaw's long-term memory layer.
 *
 * Only mark true for:
 *   - explicit "remember that…" / "from now on…"
 *   - stated preferences
 *   - identity/contact corrections
 *   - recurring routines
 *   - business facts
 *   - important personal operating context
 *
 * Most Android reflexes (torch, volume, timer) are NOT memory candidates.
 */
@Singleton
class MemoryCandidateDetector @Inject constructor() {

    private val memoryPatterns = listOf(
        // Explicit memory requests
        Regex("remember (that |this )?", RegexOption.IGNORE_CASE),
        Regex("don'?t forget", RegexOption.IGNORE_CASE),
        Regex("keep (in mind|a note)", RegexOption.IGNORE_CASE),
        Regex("note (that |this )?", RegexOption.IGNORE_CASE),
        Regex("make (a )?note", RegexOption.IGNORE_CASE),

        // Preferences / settings / routines
        Regex("from now on", RegexOption.IGNORE_CASE),
        Regex("always (do|say|use|call|reply|respond|start|stop|open)", RegexOption.IGNORE_CASE),
        Regex("never (do|say|use|call|reply|respond|start|stop|open)", RegexOption.IGNORE_CASE),
        Regex("i prefer", RegexOption.IGNORE_CASE),
        Regex("i (always|usually|normally|typically|generally)", RegexOption.IGNORE_CASE),
        Regex("my (default|usual|regular|preferred)", RegexOption.IGNORE_CASE),
        Regex("every (morning|evening|day|week|monday|tuesday|wednesday|thursday|friday)", RegexOption.IGNORE_CASE),

        // Identity / contact corrections
        Regex("my (name is|nickname|full name|real name)", RegexOption.IGNORE_CASE),
        Regex("(call|refer to) me (as )?", RegexOption.IGNORE_CASE),
        Regex("my (wife|husband|partner|girlfriend|boyfriend|mum|mom|dad|father|mother|brother|sister) is", RegexOption.IGNORE_CASE),
        Regex("(\\w+)'s (number|phone|email|address) is", RegexOption.IGNORE_CASE),

        // Business facts
        Regex("my (shop|store|business|etsy|ebay|shopify|company) is", RegexOption.IGNORE_CASE),
        Regex("my (etsy|ebay|shopify) (shop|store|account)", RegexOption.IGNORE_CASE),
        Regex("my (vat|tax|registration|company) number is", RegexOption.IGNORE_CASE),

        // Operating context
        Regex("i (live|am based|work) (in|at|near)", RegexOption.IGNORE_CASE),
        Regex("my (home|office|base|hq) is", RegexOption.IGNORE_CASE),
        Regex("i('?m| am) (a |an )?(developer|designer|writer|teacher|doctor|nurse|engineer)", RegexOption.IGNORE_CASE),
    )

    fun isMemoryCandidate(text: String): Boolean {
        val trimmed = text.trim()
        return memoryPatterns.any { it.containsMatchIn(trimmed) }
    }
}
