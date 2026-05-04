package ai.openclaw.jarvis.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesClient @Inject constructor() {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Submit [text] to the Hermes relay at [hostname]:8765 and poll until a
     * response arrives.  Returns the response string, or null on timeout (30 s)
     * or network failure.
     */
    suspend fun query(text: String, hostname: String): String? {
        val base = "http://$hostname:8765"

        val sessionId = try {
            val resp: HermesSessionResponse = http.post("$base/session") {
                contentType(ContentType.Application.Json)
                setBody(HermesSessionRequest(text = text))
            }.body()
            resp.sessionId
        } catch (_: Exception) {
            return null
        }

        val deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline) {
            delay(500)
            try {
                val poll: HermesPollResponse = http.get("$base/session/$sessionId").body()
                if (poll.status == "ready" && poll.response != null) return poll.response
            } catch (_: Exception) {}
        }
        return null
    }

    suspend fun isReachable(hostname: String): Boolean = try {
        val resp: HermesHealthResponse = http.get("http://$hostname:8765/health").body()
        resp.status == "ok"
    } catch (_: Exception) {
        false
    }
}

@Serializable
private data class HermesSessionRequest(val text: String)

@Serializable
private data class HermesSessionResponse(
    @SerialName("session_id") val sessionId: String,
    val status: String,
)

@Serializable
private data class HermesPollResponse(
    val status: String,
    val response: String? = null,
)

@Serializable
private data class HermesHealthResponse(val status: String)
