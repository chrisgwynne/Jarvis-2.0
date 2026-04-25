package ai.openclaw.jarvis.githubissues.api

import ai.openclaw.jarvis.githubissues.model.IssueEvent

/**
 * Title format per spec:
 *   `[Jarvis][<severity>][<category>] Short description`
 *
 * Description is trimmed to keep titles inside the GitHub 256-char ceiling
 * once the prefix is added.
 */
object IssueTitleFormatter {
    private const val MAX_DESC = 180

    fun format(event: IssueEvent): String {
        val desc = event.shortDescription
            .lineSequence().firstOrNull().orEmpty()
            .trim()
            .let { if (it.length > MAX_DESC) it.take(MAX_DESC - 1) + "…" else it }
        return "[Jarvis][${event.severity.tag}][${event.category.tag}] $desc"
    }
}
