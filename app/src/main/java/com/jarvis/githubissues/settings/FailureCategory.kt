package com.jarvis.githubissues.settings

/**
 * High-level categories used for both per-category opt-in toggles
 * (in settings) and for dedupe fingerprinting / issue title tagging.
 */
enum class FailureCategory(val tag: String) {
    ERROR("error"),
    UNSUPPORTED("unsupported"),
    PERMISSION("permission"),
    OPENCLAW_OFFLINE("openclaw_offline"),
    OPENCLAW_MALFORMED("openclaw_malformed"),
    OPENCLAW("openclaw"),
    CANT_DO_THAT("cant_do_that"),
    REPEATED_STT_TTS("repeated_stt_tts"),
    VOICE("voice"),
    ACTION("action"),
    INTENT("intent"),
    ROUTING("routing"),
    USER_CORRECTION("user_correction"),
    ERROR_RECOVERY("error_recovery");

    companion object {
        fun fromTag(tag: String): FailureCategory =
            values().firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ERROR
    }
}
