package com.jarvis.githubissues.redaction

/**
 * Pure-text redactor. Operates on already-extracted strings — it never sees
 * raw audio, full SMS bodies that the policy disallows, or auth tokens that
 * have already been stripped at their source.
 *
 * Patterns intentionally err on the side of over-redacting. Numbers that
 * could be phone numbers, anything resembling an email, GH/openclaw token
 * shapes, and bracketed lat/long coordinates are all replaced.
 */
class Redactor(private val policy: RedactionPolicy) {

    fun redact(input: String?, knownContactNames: Collection<String> = emptyList()): String? {
        if (input.isNullOrEmpty()) return input
        var out = input

        // Tokens first — long opaque strings can otherwise be partly matched as
        // phone or email patterns and leak the prefix.
        if (policy.redactTokens()) {
            out = TOKEN_PATTERNS.fold(out) { acc, regex -> regex.replace(acc, TOKEN_MASK) }
        }
        if (policy.redactOpenClawKeys()) {
            out = OPENCLAW_KEY_PATTERN.replace(out, OC_KEY_MASK)
        }
        if (policy.redactEmail()) {
            out = EMAIL_PATTERN.replace(out, EMAIL_MASK)
        }
        if (policy.redactPhone()) {
            out = PHONE_PATTERN.replace(out, PHONE_MASK)
        }
        if (policy.redactLocation()) {
            out = LATLONG_PATTERN.replace(out, LOCATION_MASK)
            out = ADDRESS_HINT_PATTERN.replace(out, LOCATION_MASK)
        }
        if (policy.redactContactNames() && knownContactNames.isNotEmpty()) {
            for (name in knownContactNames.sortedByDescending { it.length }) {
                if (name.isBlank()) continue
                out = out.replace(name, CONTACT_MASK, ignoreCase = true)
            }
        }

        return out
    }

    /**
     * Apply message-body redaction only when the user has not opted-in to
     * including bodies. Returns either the original body or a length-only marker.
     */
    fun redactMessageBody(body: String?): String? {
        if (body == null) return null
        if (!policy.redactMessageBody()) return body
        if (body.isEmpty()) return body
        return "<redacted ${body.length} chars>"
    }

    /** Restricted-command transcripts (banking / health / etc.). */
    fun redactRestrictedTranscript(transcript: String?): String? {
        if (transcript == null) return null
        if (!policy.redactRestrictedTranscripts()) return transcript
        return "<redacted transcript>"
    }

    companion object {
        private const val TOKEN_MASK = "<redacted-token>"
        private const val OC_KEY_MASK = "<redacted-openclaw-key>"
        private const val EMAIL_MASK = "<redacted-email>"
        private const val PHONE_MASK = "<redacted-phone>"
        private const val LOCATION_MASK = "<redacted-location>"
        private const val CONTACT_MASK = "<redacted-contact>"

        private val EMAIL_PATTERN =
            Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

        // International or local phone numbers, 7+ digits, allowing spaces / dashes / parens.
        private val PHONE_PATTERN =
            Regex("""(?<!\w)(\+?\d[\d\s().\-]{6,}\d)(?!\w)""")

        // GitHub PATs (ghp_, github_pat_), generic Bearer tokens, AWS-ish keys,
        // and any 32+-char hex/base64 blob.
        private val TOKEN_PATTERNS = listOf(
            Regex("""ghp_[A-Za-z0-9]{20,}"""),
            Regex("""github_pat_[A-Za-z0-9_]{20,}"""),
            Regex("""(?i)bearer\s+[A-Za-z0-9._\-]{16,}"""),
            Regex("""(?i)authorization:\s*token\s+[A-Za-z0-9._\-]{16,}"""),
            Regex("""AKIA[0-9A-Z]{16}"""),
            Regex("""(?<![A-Za-z0-9])[A-Fa-f0-9]{32,}(?![A-Za-z0-9])""")
        )

        private val OPENCLAW_KEY_PATTERN =
            Regex("""(?i)(?:openclaw|oc)[_\-]?(?:key|token|secret)\s*[:=]\s*\S+""")

        private val LATLONG_PATTERN =
            Regex("""[-+]?\d{1,3}\.\d{3,}\s*,\s*[-+]?\d{1,3}\.\d{3,}""")

        private val ADDRESS_HINT_PATTERN =
            Regex("""\b\d+\s+[A-Z][a-z]+\s+(?:Street|St|Road|Rd|Avenue|Ave|Lane|Ln|Drive|Dr)\b""")
    }
}
