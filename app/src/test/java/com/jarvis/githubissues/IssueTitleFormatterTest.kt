package com.jarvis.githubissues

import com.jarvis.githubissues.api.IssueTitleFormatter
import com.jarvis.githubissues.model.IssueContext
import com.jarvis.githubissues.model.IssueEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueTitleFormatterTest {

    @Test fun `formats permission denied per spec`() {
        val title = IssueTitleFormatter.format(
            IssueEvent.PermissionDenied(
                permission = "SMS",
                action = "send",
                context = IssueContext()
            )
        )
        // [Jarvis][error][permission] Permission missing: SMS for send
        assertTrue(title, title.startsWith("[Jarvis][error][permission] "))
        assertTrue(title, title.contains("SMS"))
    }

    @Test fun `formats unsupported per spec`() {
        val title = IssueTitleFormatter.format(
            IssueEvent.Unsupported(
                capability = "answer_call",
                reason = "Android restriction",
                userPhrase = "answer the call",
                context = IssueContext()
            )
        )
        assertTrue(title, title.startsWith("[Jarvis][warning][unsupported] "))
        assertTrue(title, title.contains("answer_call"))
    }

    @Test fun `formats malformed openclaw response`() {
        val title = IssueTitleFormatter.format(
            IssueEvent.OpenClawFailure(
                mode = IssueEvent.OpenClawFailure.Mode.MALFORMED_RESPONSE,
                errorCode = "bad_json",
                message = null,
                context = IssueContext()
            )
        )
        assertTrue(title, title.startsWith("[Jarvis][error][openclaw_malformed] "))
    }

    @Test fun `truncates very long descriptions`() {
        val long = "x".repeat(500)
        val title = IssueTitleFormatter.format(
            IssueEvent.CantDoThat(userPhrase = null, reason = long, context = IssueContext())
        )
        // Prefix length plus the truncated short description should be ≤ 256.
        assertTrue(title.length <= 256)
        assertEquals('…', title.last())
    }
}
