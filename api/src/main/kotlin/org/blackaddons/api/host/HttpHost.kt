package org.blackaddons.api.host

/**
 * Shared pooled HTTP client with a response size cap. [send]/[get]/[bytes] are async and call back on the
 * client thread; `*Blocking` variants throw if called on it. Deliberately cannot send the BlackAddons session
 * - use `AddonContext.requestAssertion`.
 */
interface HttpHost {

    enum class Method { GET, POST, PUT, DELETE }

    /** [body] is empty for 204/304. [etag] is for conditional requests. */
    data class Response(val status: Int, val body: String, val etag: String?)

    /** Null to [onResult] on transport error or oversized body (logged). [ifNoneMatch]: conditional, 304 = no body. */
    fun send(
        url: String,
        method: Method = Method.GET,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
        ifNoneMatch: String? = null,
        onResult: (Response?) -> Unit,
    )

    fun get(url: String, headers: Map<String, String> = emptyMap(), onResult: (Response?) -> Unit) =
        send(url, headers = headers, onResult = onResult)

    /** [onResult] gets null on failure. */
    fun bytes(url: String, onResult: (ByteArray?) -> Unit)

    fun sendBlocking(
        url: String,
        method: Method = Method.GET,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
        ifNoneMatch: String? = null,
    ): Response?

    fun getBlocking(url: String, headers: Map<String, String> = emptyMap()): Response? =
        sendBlocking(url, headers = headers)

    fun bytesBlocking(url: String): ByteArray?
}
