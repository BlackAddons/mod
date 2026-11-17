package org.blackaddons.core.backend

import org.blackaddons.api.Platform
import org.blackaddons.api.host.HttpHost
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.log.Log

/** [HttpHost] over [Http]. An adapter, so addons can never get the player session or the 401 re-auth. */
internal object SharedHttp : HttpHost {

    private const val ASYNC_THREADS = 4

    private val async = Workers.pool("http", ASYNC_THREADS)

    override fun send(
        url: String,
        method: HttpHost.Method,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
        ifNoneMatch: String?,
        onResult: (HttpHost.Response?) -> Unit,
    ) = later(onResult) { request(url, method, body, contentType, headers, ifNoneMatch) }

    override fun bytes(url: String, onResult: (ByteArray?) -> Unit) = later(onResult) { Http.bytes(url) }

    override fun sendBlocking(
        url: String,
        method: HttpHost.Method,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
        ifNoneMatch: String?,
    ): HttpHost.Response? {
        offClientThread("sendBlocking")
        return request(url, method, body, contentType, headers, ifNoneMatch)
    }

    override fun bytesBlocking(url: String): ByteArray? {
        offClientThread("bytesBlocking")
        return Http.bytes(url)
    }

    // A request can take up to its 15s timeout. On the client thread, that freezes the game.
    private fun offClientThread(call: String) {
        check(!Platform.screenHost.isClientThread) {
            "Platform.http.$call blocks and would freeze the game on the client thread; use the callback form, or call it from OffThread"
        }
    }

    // Always answers on the client thread. A throw becomes null.
    private fun <T> later(onResult: (T?) -> Unit, work: () -> T?) {
        async.execute {
            val value = runCatching(work).onFailure { Log.main.warn("HTTP request failed", it) }.getOrNull()
            Platform.screenHost.onClientThread { onResult(value) }
        }
    }

    private fun request(
        url: String,
        method: HttpHost.Method,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
        ifNoneMatch: String?,
    ): HttpHost.Response? = Http.send(
        url = url,
        method = when (method) {
            HttpHost.Method.GET -> Http.Method.GET
            HttpHost.Method.POST -> Http.Method.POST
            HttpHost.Method.PUT -> Http.Method.PUT
            HttpHost.Method.DELETE -> Http.Method.DELETE
        },
        body = body,
        contentType = contentType,
        headers = headers,
        ifNoneMatch = ifNoneMatch,
    )?.let { HttpHost.Response(it.status, it.body, it.etag) }
}
