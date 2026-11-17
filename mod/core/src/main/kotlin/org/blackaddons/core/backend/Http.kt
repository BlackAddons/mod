package org.blackaddons.core.backend

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.core.backend.auth.BackendSession
import org.blackaddons.core.backend.gateway.stringOrNull
import org.blackaddons.core.log.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration

/**
 * The mod's single pooled HTTP client. Reused connections skip TLS and DNS (see [BackendDns]). It owns timeouts,
 * User-Agent, the player-JWT 401 retry and a response size cap. Blocking, so call it off the client thread.
 */
object Http {

    @Volatile private var client: HttpClient = newClient()

    private fun newClient(): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    internal fun reset() {
        val old = client
        client = newClient()
        NetLog.trace("HTTP client replaced; closing the old connection pool")
        runCatching { old.shutdownNow() }
    }

    enum class Method { GET, POST, PUT, DELETE }

    /**
     * [body] is empty for 204/304. Every 429/503 comes with [retryAfterSeconds], the only wait to honor.
     * [unavailableReason] (`database`/`cache`) marks a 503 from the whole backend, not from one endpoint.
     */
    data class Response(
        val status: Int,
        val body: String,
        val etag: String?,
        val retryAfterSeconds: Int? = null,
        val unavailableReason: String? = null,
    ) {

        /** Rate-limited or degraded, says nothing about credentials. */
        val busy: Boolean get() = status == 429 || status == 503

        /**
         * Backend-wide degradation, never an auth failure, so don't drop tokens. Uses the reason header, because some
         * endpoints send 503 for their own reasons (e.g. Discord not configured).
         */
        val unavailable: Boolean get() = status == 503 && unavailableReason != null

        /** The backend's own message for a refusal (`{"error": "..."}`). Null when the body has none. */
        val error: String? get() = runCatching { JsonParser.parseString(body).asJsonObject.stringOrNull("error") }.getOrNull()
    }

    /**
     * Null when there's no session (if [authenticated]), on transport failure, or when the body is bigger than
     * [maxBytes]. All of them get logged. [authenticated] attaches the JWT, and on a 401 it authenticates again and
     * resends once. [ifNoneMatch] makes the request conditional.
     */
    fun send(
        url: String,
        method: Method = Method.GET,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
        authenticated: Boolean = false,
        ifNoneMatch: String? = null,
        maxBytes: Int = MAX_BODY_BYTES,
    ): Response? = deliver(url, method, publisher(body), contentType, headers, authenticated, ifNoneMatch, maxBytes)

    fun send(
        url: String,
        bytes: ByteArray,
        method: Method = Method.POST,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        authenticated: Boolean = false,
        maxBytes: Int = MAX_BODY_BYTES,
    ): Response? = deliver(
        url, method, HttpRequest.BodyPublishers.ofByteArray(bytes), contentType, headers,
        authenticated, ifNoneMatch = null, maxBytes = maxBytes,
    )

    private fun deliver(
        url: String,
        method: Method,
        payload: HttpRequest.BodyPublisher,
        contentType: String?,
        headers: Map<String, String>,
        authenticated: Boolean,
        ifNoneMatch: String?,
        maxBytes: Int,
    ): Response? {
        if (!authenticated) {
            return exchange(url, method, payload, contentType, headers, ifNoneMatch, maxBytes, token = null)
        }

        val token = BackendSession.validToken() ?: return null
        val first = exchange(url, method, payload, contentType, headers, ifNoneMatch, maxBytes, token)
        if (first == null || first.status != 401) return first

        // Stale token: authenticate again and resend once. The body publishers here can send their content again.
        BackendSession.invalidate(token)
        val fresh = BackendSession.validToken() ?: return null
        return exchange(url, method, payload, contentType, headers, ifNoneMatch, maxBytes, fresh)
    }

    internal fun webSocketBuilder(): WebSocket.Builder = client.newWebSocketBuilder()

    fun get(url: String, headers: Map<String, String> = emptyMap(), authenticated: Boolean = false): Response? =
        send(url, headers = headers, authenticated = authenticated)

    /** Set [authenticated] for bytes behind the player's token (ticket pictures). */
    fun bytes(
        url: String,
        maxBytes: Int = MAX_BODY_BYTES,
        authenticated: Boolean = false,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
    ): ByteArray? = runCatching {
        val token = if (authenticated) BackendSession.validToken() ?: return null else null
        val request = builder(url, accept = null)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            Log.main.warn("GET {} returned HTTP {}", url, response.statusCode())
            return null
        }
        readCapped(response, url, maxBytes, onProgress)
    }.getOrElse {
        Log.main.warn("GET {} failed: {}", url, it.toString())
        null
    }

    private fun exchange(
        url: String,
        method: Method,
        payload: HttpRequest.BodyPublisher,
        contentType: String?,
        headers: Map<String, String>,
        ifNoneMatch: String?,
        maxBytes: Int,
        token: String?,
    ): Response? = runCatching {
        val request = builder(url).apply {
            token?.let { header("Authorization", "Bearer $it") }
            contentType?.let { header("Content-Type", it) }
            ifNoneMatch?.let { header("If-None-Match", it) }
            headers.forEach { (name, value) -> header(name, value) }
            when (method) {
                Method.GET -> GET()
                Method.POST -> POST(payload)
                Method.PUT -> PUT(payload)
                Method.DELETE -> DELETE()
            }
        }.build()

        val startedAt = System.currentTimeMillis()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        NetLog.trace("{} {} -> {} in {} ms", method, url, response.statusCode(), System.currentTimeMillis() - startedAt)
        val bytes = readCapped(response, url, maxBytes) ?: return null
        Response(
            status = response.statusCode(),
            body = String(bytes, Charsets.UTF_8),
            etag = response.headers().firstValue("ETag").orElse(null),
            retryAfterSeconds = response.headers().firstValue("Retry-After").orElse(null)?.toIntOrNull(),
            unavailableReason = response.headers().firstValue("X-Unavailable-Reason").orElse(null),
        ).also { observe(it, url) }
    }.getOrElse {
        Log.main.warn("{} {} failed: {}", method, url, it.toString())
        null
    }

    /** The reason header tells "backend down", "a dependency down" and "one endpoint refusing" apart. */
    private fun observe(response: Response, url: String) {
        // Show a degraded backend. A 429 only means we ask too often.
        if (response.unavailable) BackendStatus.onUnavailable() else if (response.status < 400) BackendStatus.onReached()
        if (!response.busy) return
        Log.main.warn(
            "{} refused with HTTP {}{} - retry in {}s",
            url, response.status,
            response.unavailableReason?.let { " ($it unavailable)" } ?: "",
            response.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS,
        )
    }

    private fun builder(url: String, accept: String? = "application/json"): HttpRequest.Builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .apply { accept?.let { header("Accept", it) } }
            .timeout(Duration.ofSeconds(15))

    private fun publisher(body: String?): HttpRequest.BodyPublisher =
        if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)

    private fun readCapped(
        response: HttpResponse<InputStream>,
        url: String,
        maxBytes: Int,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
    ): ByteArray? {
        if (onProgress != null) return readCappedWithProgress(response, url, maxBytes, onProgress)
        val bytes = response.body().use { it.readNBytes(maxBytes + 1) }
        if (bytes.size <= maxBytes) return bytes
        Log.main.warn("Response from {} exceeds {} bytes - refusing it", url, maxBytes)
        return null
    }

    private fun readCappedWithProgress(
        response: HttpResponse<InputStream>,
        url: String,
        maxBytes: Int,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): ByteArray? {
        val declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        val total = declared.takeIf { it >= 0L }
        if (declared > maxBytes) {
            response.body().close()
            Log.main.warn("Response from {} exceeds {} bytes - refusing it", url, maxBytes)
            return null
        }
        onProgress(0L, total)
        val initialSize = declared.takeIf { it in 1..maxBytes.toLong() }?.toInt() ?: DEFAULT_PROGRESS_BUFFER
        val output = ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(DEFAULT_PROGRESS_BUFFER)
        response.body().use { input ->
            var received = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                received += count
                if (received > maxBytes) {
                    Log.main.warn("Response from {} exceeds {} bytes - refusing it", url, maxBytes)
                    return null
                }
                output.write(buffer, 0, count)
                onProgress(received, total)
            }
        }
        return output.toByteArray()
    }

    /**
     * Our builds by version in the backend's client stats, next to anything else that calls it. Lazy, because the
     * platform gets bound after this object exists.
     */
    private val userAgent by lazy { "BlackAddons/${Platform.blackaddonsVersion} (Minecraft ${Platform.minecraftVersion})" }

    /** Only used for logs when the header is missing. */
    private const val DEFAULT_RETRY_SECONDS = 15
    private const val DEFAULT_PROGRESS_BUFFER = 8192
    private const val MAX_BODY_BYTES = 10 * 1024 * 1024
}
