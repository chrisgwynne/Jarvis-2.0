package com.jarvis.githubissues.redaction

import com.jarvis.githubissues.settings.RedactionSettings

/**
 * View on top of [RedactionSettings] that callers ask "should I redact X?".
 * Keeping this behind a single seam lets us evolve redaction defaults without
 * touching every site that emits an [com.jarvis.githubissues.model.IssueEvent].
 */
class RedactionPolicy(private val settings: RedactionSettings) {
    fun redactPhone(): Boolean = settings.redactPhoneNumbers
    fun redactEmail(): Boolean = settings.redactEmails
    fun redactLocation(): Boolean = settings.redactPreciseLocation
    fun redactMessageBody(): Boolean = settings.redactMessageBody
    fun redactContactNames(): Boolean = settings.redactContactNames
    fun redactRestrictedTranscripts(): Boolean = settings.redactRestrictedTranscripts
    fun redactTokens(): Boolean = settings.redactTokens
    fun redactOpenClawKeys(): Boolean = settings.redactOpenClawKeys
}
