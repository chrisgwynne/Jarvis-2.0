package ai.openclaw.jarvis.githubissues.settings

enum class Severity(val rank: Int, val tag: String) {
    INFO(0, "info"),
    WARNING(1, "warning"),
    ERROR(2, "error"),
    CRITICAL(3, "critical");

    fun atLeast(other: Severity): Boolean = rank >= other.rank

    companion object {
        fun fromTag(tag: String): Severity =
            values().firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: WARNING
    }
}
