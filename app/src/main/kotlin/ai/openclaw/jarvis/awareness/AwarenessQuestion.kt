package ai.openclaw.jarvis.awareness

/**
 * Recognised user questions about Jarvis's own capabilities.
 *
 * Detection is intentionally simple substring matching against the
 * lower-cased transcript — the goal isn't to NLP this perfectly, it's to
 * make sure the *common* phrasings get a local, accurate answer instead
 * of a freeform OpenClaw guess.
 */
sealed class AwarenessQuestion {
    object WhatCanYouDo : AwarenessQuestion()
    object MissingPermissions : AwarenessQuestion()
    object WhyCantYouDoThat : AwarenessQuestion()

    /**
     * "Can you X?" — the [topic] decides which slice of the snapshot
     * the responder reads.
     */
    data class CanYou(val topic: Topic) : AwarenessQuestion()

    enum class Topic {
        SMS,
        WHATSAPP,
        CALL,
        EMAIL,
        SCREENSHOT,
        SCREEN,
        LOCATION,
        OPEN_CLAW,
        OPEN_APP,
        PHOTO,
    }
}

/**
 * Phrase-matching detector. Checks "what can you do" first, then specific
 * "can you X?" topics, then misc questions. Returns null when the
 * transcript isn't an awareness question.
 */
object AwarenessQuestionDetector {

    private val WHAT_CAN_YOU_DO = listOf(
        "what can you do",
        "what are you able to",
        "what are you capable of",
        "list your capabilities",
        "list your skills",
        "tell me what you can do",
    )

    private val MISSING_PERMS = listOf(
        "what permissions are missing",
        "which permissions are missing",
        "what permissions do you need",
        "what's missing",
        "what is missing",
    )

    private val WHY_CANT = listOf(
        "why can't you",
        "why cant you",
        "why won't you",
        "why wont you",
    )

    private val TOPICS: List<Pair<List<String>, AwarenessQuestion.Topic>> = listOf(
        listOf("send whatsapp", "send a whatsapp", "use whatsapp", "whatsapp")
            to AwarenessQuestion.Topic.WHATSAPP,
        listOf("send sms", "send a text", "send text", "text my", "message via sms")
            to AwarenessQuestion.Topic.SMS,
        listOf("call ", "make a call", "phone someone", "ring my", "ring someone")
            to AwarenessQuestion.Topic.CALL,
        listOf("email", "send email", "send an email")
            to AwarenessQuestion.Topic.EMAIL,
        listOf("screenshot", "capture screen", "take screenshot", "take a screenshot")
            to AwarenessQuestion.Topic.SCREENSHOT,
        listOf("see my screen", "look at my screen", "read my screen", "see the screen")
            to AwarenessQuestion.Topic.SCREEN,
        listOf("my location", "use my location", "where am i", "share my location", "get my location")
            to AwarenessQuestion.Topic.LOCATION,
        listOf("openclaw", "open claw", "access openclaw")
            to AwarenessQuestion.Topic.OPEN_CLAW,
        listOf("open spotify", "open the app", "launch the app", "open an app")
            to AwarenessQuestion.Topic.OPEN_APP,
        listOf("take a photo", "take photo", "take a picture", "take picture")
            to AwarenessQuestion.Topic.PHOTO,
    )

    fun detect(rawTranscript: String): AwarenessQuestion? {
        val t = rawTranscript.trim().lowercase()
        if (t.isEmpty()) return null

        if (WHAT_CAN_YOU_DO.any { t.contains(it) }) return AwarenessQuestion.WhatCanYouDo
        if (MISSING_PERMS.any { t.contains(it) }) return AwarenessQuestion.MissingPermissions

        // "Can you X?" patterns. Require the phrase to start with a "can you"
        // / "could you" / "are you able to" lead-in, otherwise normal
        // command-mode utterances like "send an email to Dave" would loop
        // back as awareness questions.
        val isAsking = t.startsWith("can you") ||
            t.startsWith("could you") ||
            t.startsWith("are you able to") ||
            t.contains("can you ")

        if (isAsking) {
            for ((phrases, topic) in TOPICS) {
                if (phrases.any { t.contains(it) }) return AwarenessQuestion.CanYou(topic)
            }
        }

        if (WHY_CANT.any { t.contains(it) }) return AwarenessQuestion.WhyCantYouDoThat

        return null
    }
}
