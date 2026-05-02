package ai.openclaw.jarvis.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenClawConnectionTester @Inject constructor() {

    data class ParsedUrl(
        val isValid: Boolean,
        val scheme: String = "",
        val host: String = "",
        val port: Int = -1,
        val path: String = "/",
        val normalizedWsUrl: String = "",
        val normalizedHttpUrl: String = "",
        val isTls: Boolean = false,
        val error: String = "",
    )

    data class TestResult(
        val steps: List<TestStep>,
        val parsedUrl: ParsedUrl,
        val workingWsPath: String? = null,
    )

    fun parseAndNormalize(rawUrl: String): ParsedUrl {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ParsedUrl(false, error = "URL is empty")

        val withScheme = when {
            trimmed.startsWith("wss://")   -> trimmed
            trimmed.startsWith("ws://")    -> trimmed
            trimmed.startsWith("https://") -> trimmed.replace("https://", "wss://")
            trimmed.startsWith("http://")  -> trimmed.replace("http://", "ws://")
            trimmed.contains("://") -> return ParsedUrl(
                false, error = "Unknown scheme. Use ws://, wss://, http://, or https://"
            )
            else -> "ws://$trimmed"
        }

        return try {
            val uri = URI(withScheme)
            val scheme = uri.scheme ?: "ws"
            val host = uri.host
                ?: return ParsedUrl(false, error = "Cannot parse host from URL. Check the format.")
            val port = when {
                uri.port > 0 -> uri.port
                scheme == "wss" -> 443
                else -> 80
            }
            val path = uri.path?.takeIf { it.isNotBlank() } ?: "/"
            val isTls = scheme == "wss"
            ParsedUrl(
                isValid = true,
                scheme = scheme,
                host = host,
                port = port,
                path = path,
                normalizedWsUrl = "$scheme://$host:$port$path",
                normalizedHttpUrl = "${if (isTls) "https" else "http"}://$host:$port",
                isTls = isTls,
            )
        } catch (e: Exception) {
            ParsedUrl(false, error = "Invalid URL: ${e.message}")
        }
    }

    suspend fun runTest(
        rawUrl: String,
        onStepUpdate: (List<TestStep>) -> Unit,
    ): TestResult {
        val steps = mutableListOf<TestStep>()

        fun emit() = onStepUpdate(steps.toList())
        fun update(index: Int, state: TestStepState, detail: String = "", suggestion: String = "") {
            steps[index] = steps[index].copy(state = state, detail = detail, suggestion = suggestion)
            emit()
        }

        // ── Step 1: URL format ────────────────────────────────────────────────
        steps += TestStep("URL format", TestStepState.RUNNING); emit()
        val parsed = parseAndNormalize(rawUrl)
        if (!parsed.isValid) {
            update(0, TestStepState.FAIL, parsed.error,
                "Example: ws://192.168.1.100:8765  or  ws://100.x.x.x:8765/ws")
            return TestResult(steps, parsed)
        }
        update(0, TestStepState.PASS,
            "Normalized → ${parsed.normalizedWsUrl}  (${parsed.scheme.uppercase()}, ${if (parsed.isTls) "TLS" else "plaintext"})")

        // ── Step 2: TCP reachability ──────────────────────────────────────────
        steps += TestStep("TCP reachability (${parsed.host}:${parsed.port})", TestStepState.RUNNING); emit()
        val tcpErr = withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { it.connect(InetSocketAddress(parsed.host, parsed.port), 6_000) }
            }.exceptionOrNull()?.message
        }
        if (tcpErr != null) {
            val suggestion = when {
                tcpErr.contains("refused", ignoreCase = true) ->
                    "Port ${parsed.port} is closed. Is OpenClaw running? Check the port number."
                tcpErr.contains("timeout", ignoreCase = true) || tcpErr.contains("timed out", ignoreCase = true) ->
                    "Host ${parsed.host} not responding. Check Tailscale is connected and the host is online."
                tcpErr.contains("unreachable", ignoreCase = true) || tcpErr.contains("No route", ignoreCase = true) ->
                    "Network unreachable. Check Tailscale is active or you're on the same LAN."
                tcpErr.contains("resolve", ignoreCase = true) || tcpErr.contains("Unknown host", ignoreCase = true) ->
                    "Hostname '${parsed.host}' could not be resolved. Use the IP address instead."
                else -> "Cannot reach ${parsed.host}:${parsed.port}."
            }
            update(1, TestStepState.FAIL, tcpErr, suggestion)
            return TestResult(steps, parsed)
        }
        update(1, TestStepState.PASS, "Port ${parsed.port} is open")

        // ── Step 3: HTTP health check ─────────────────────────────────────────
        steps += TestStep("HTTP health check", TestStepState.RUNNING); emit()
        val httpResult = withContext(Dispatchers.IO) { probeHttp(parsed.normalizedHttpUrl) }
        if (httpResult != null) {
            update(2, TestStepState.PASS, httpResult)
        } else {
            update(2, TestStepState.SKIPPED, "No HTTP response — gateway may be WebSocket-only (this is normal)")
        }

        // ── Step 4: WebSocket path probe ──────────────────────────────────────
        steps += TestStep("WebSocket handshake", TestStepState.RUNNING); emit()
        val pathsToProbe = buildList {
            if (parsed.path.isNotBlank() && parsed.path != "/") add(parsed.path)
            addAll(listOf("/", "/ws", "/gateway/ws", "/gateway", "/api/ws"))
        }.distinct()

        val wsResult = withContext(Dispatchers.IO) {
            probeWsPaths(parsed.host, parsed.port, pathsToProbe, parsed.isTls)
        }
        val workingPath: String?
        when {
            wsResult.successPath != null -> {
                workingPath = wsResult.successPath
                val pathNote = if (wsResult.successPath != parsed.path)
                    " — update your URL to use this path" else ""
                update(3, TestStepState.PASS, "WebSocket accepted at ${wsResult.successPath}$pathNote",
                    if (wsResult.successPath != parsed.path)
                        "Working path: ${wsResult.successPath}. Your current URL uses '${parsed.path}' — update it."
                    else "")
            }
            wsResult.tlsError -> {
                workingPath = null
                update(3, TestStepState.FAIL,
                    "TLS/certificate error: ${wsResult.error}",
                    "Using wss:// but the server has no valid TLS cert. Try ws:// instead for Tailscale LAN connections.")
            }
            else -> {
                workingPath = null
                val tried = pathsToProbe.joinToString(", ")
                update(3, TestStepState.FAIL,
                    "No path accepted WebSocket upgrade. Tried: $tried",
                    "Server responded but rejected all WebSocket paths. Check OpenClaw docs for the correct WebSocket endpoint.")
            }
        }

        // ── Step 5: Protocol note ─────────────────────────────────────────────
        steps += TestStep("OpenClaw protocol", TestStepState.RUNNING); emit()
        if (workingPath != null) {
            update(4, TestStepState.PASS,
                "WebSocket open. OpenClaw node.connect frame will be sent on next connection attempt.")
        } else {
            update(4, TestStepState.SKIPPED, "Cannot test protocol — WebSocket not reachable")
        }

        return TestResult(steps, parsed, workingPath)
    }

    // ── HTTP probe ────────────────────────────────────────────────────────────

    private fun probeHttp(baseUrl: String): String? {
        for (path in listOf("/health", "/status", "/")) {
            runCatching {
                val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                conn.disconnect()
                if (code in 100..499) return "HTTP $code at $path"
            }
        }
        return null
    }

    // ── WebSocket path probe ──────────────────────────────────────────────────

    private data class WsProbeResult(
        val successPath: String? = null,
        val tlsError: Boolean = false,
        val error: String = "",
    )

    private fun probeWsPaths(host: String, port: Int, paths: List<String>, tls: Boolean): WsProbeResult {
        for (path in paths) {
            when (val r = probeWsPath(host, port, path, tls)) {
                "101"     -> return WsProbeResult(successPath = path)
                "TLS_ERR" -> return WsProbeResult(tlsError = true, error = "Certificate or TLS handshake failed")
                else      -> { /* try next path */ }
            }
        }
        return WsProbeResult(error = "No path accepted WebSocket upgrade")
    }

    private fun probeWsPath(host: String, port: Int, path: String, tls: Boolean): String {
        return try {
            val socket: Socket = if (tls) {
                runCatching {
                    javax.net.ssl.SSLSocketFactory.getDefault().createSocket(host, port) as Socket
                }.getOrElse { return "TLS_ERR" }
            } else {
                Socket()
            }
            socket.soTimeout = 6_000
            if (!tls) socket.connect(InetSocketAddress(host, port), 6_000)
            socket.use { s ->
                val key = "dGhlIHNhbXBsZSBub25jZQ=="
                val request = buildString {
                    append("GET $path HTTP/1.1\r\n")
                    append("Host: $host:$port\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: $key\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    append("\r\n")
                }
                s.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                s.getOutputStream().flush()
                val firstLine = s.getInputStream().bufferedReader(Charsets.US_ASCII).readLine() ?: return "no response"
                when {
                    firstLine.contains("101") -> "101"
                    firstLine.contains("404") -> "404"
                    firstLine.contains("403") -> "403"
                    firstLine.contains("401") -> "401"
                    firstLine.contains("400") -> "400"
                    else -> firstLine.take(80)
                }
            }
        } catch (e: javax.net.ssl.SSLException) {
            "TLS_ERR"
        } catch (e: Exception) {
            "ERR: ${e.message?.take(60)}"
        }
    }
}
