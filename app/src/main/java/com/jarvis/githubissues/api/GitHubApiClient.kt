package com.jarvis.githubissues.api

import com.jarvis.githubissues.model.IssueDraft
import com.jarvis.githubissues.settings.SettingsSource
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin wrapper around the GitHub REST API. We deliberately use the JDK
 * HttpURLConnection so this module brings no extra Android dependencies.
 *
 * All calls are synchronous — the caller is expected to be on a background
 * coroutine / executor (the orchestrator and queue worker both are).
 */
open class GitHubApiClient(
    private val settingsRepo: SettingsSource,
    private val baseUrl: String = "https://api.github.com",
    private val userAgent: String = "Jarvis-IssueLogger"
) {

    sealed class Result {
        data class Success(val issueNumber: Int, val htmlUrl: String) : Result()
        data class Failure(val httpStatus: Int?, val message: String, val transient: Boolean) : Result()
    }

    open fun testConnection(): Result {
        val s = settingsRepo.current()
        if (s.owner.isBlank() || s.repo.isBlank()) {
            return Result.Failure(null, "owner/repo not configured", transient = false)
        }
        val token = settingsRepo.token()
            ?: return Result.Failure(null, "token not configured", transient = false)
        val url = URL("$baseUrl/repos/${s.owner}/${s.repo}")
        return runRequest(url, "GET", token, body = null) { code, _ ->
            if (code in 200..299) Result.Success(0, url.toString())
            else mapFailure(code, "test connection failed")
        }
    }

    open fun createIssue(draft: IssueDraft): Result {
        val s = settingsRepo.current()
        if (s.owner.isBlank() || s.repo.isBlank()) {
            return Result.Failure(null, "owner/repo not configured", transient = false)
        }
        val token = settingsRepo.token()
            ?: return Result.Failure(null, "token not configured", transient = false)
        val url = URL("$baseUrl/repos/${s.owner}/${s.repo}/issues")
        val payload = JSONObject()
            .put("title", draft.title)
            .put("body", draft.body)
            .put("labels", JSONArray(draft.labels))
            .toString()
        return runRequest(url, "POST", token, body = payload) { code, response ->
            if (code in 200..299) {
                val obj = JSONObject(response)
                Result.Success(obj.optInt("number"), obj.optString("html_url"))
            } else mapFailure(code, response)
        }
    }

    open fun commentOnIssue(issueNumber: Int, body: String): Result {
        val s = settingsRepo.current()
        val token = settingsRepo.token()
            ?: return Result.Failure(null, "token not configured", transient = false)
        val url = URL("$baseUrl/repos/${s.owner}/${s.repo}/issues/$issueNumber/comments")
        val payload = JSONObject().put("body", body).toString()
        return runRequest(url, "POST", token, body = payload) { code, response ->
            if (code in 200..299) {
                val obj = JSONObject(response)
                Result.Success(issueNumber, obj.optString("html_url"))
            } else mapFailure(code, response)
        }
    }

    private fun runRequest(
        url: URL,
        method: String,
        token: String,
        body: String?,
        onResponse: (Int, String) -> Result
    ): Result {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            onResponse(code, response)
        } catch (t: Throwable) {
            Result.Failure(null, t.message ?: t.javaClass.simpleName, transient = true)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun mapFailure(code: Int, response: String): Result.Failure {
        // 4xx (other than 408 / 429) is the caller's fault — treat as terminal.
        // 5xx and rate-limit are transient and queue for retry.
        val transient = code in 500..599 || code == 408 || code == 429
        return Result.Failure(code, response.take(2_000), transient)
    }
}
