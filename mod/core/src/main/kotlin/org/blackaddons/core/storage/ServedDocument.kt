package org.blackaddons.core.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log
import java.nio.file.Files
import java.nio.file.Path

/**
 * Backend-served JSON with a local copy. Disk first (usable on the first frame), then a conditional refresh off-thread
 * (`ETag`/304). Every failure is silent, and the caller keeps what it had.
 */
internal object ServedDocument {

    private const val MAX_BYTES = 1024 * 1024

    /**
     * [install] runs with the cached copy now (calling thread), and again only if the backend returns a newer copy
     * (worker), so it should only publish. [cache] is the config-relative path without extension.
     */
    fun load(url: String, cache: String, install: (JsonObject) -> Unit) {
        cached(cache)?.let { runCatching { install(it) }.onFailure { t -> failed(cache, t) } }

        OffThread.run(
            name = "document-${cache.substringAfterLast('/')}",
            work = { refresh(url, cache) },
            onFailure = { null },
            onResult = { document -> document?.let { install(it) } },
        )
    }

    private fun refresh(url: String, cache: String): JsonObject? {
        val response = Http.send(url, ifNoneMatch = etag(cache), maxBytes = MAX_BYTES) ?: return null

        // 304 is normal. 404 means it doesn't exist yet.
        if (response.status != 200) return null

        val document = parse(response.body) ?: return null
        store(cache, response.body, response.etag)
        return document
    }

    private fun cached(cache: String): JsonObject? = runCatching {
        Disk.read(file(cache, "json"))?.let(::parse)
    }.getOrElse {
        Log.main.debug("Reading cached document '{}' failed: {}", cache, it.toString())
        null
    }

    private fun etag(cache: String): String? = runCatching {
        Disk.read(file(cache, "etag"))?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Body first, then the ETag, so an interruption can only cost a revalidation. */
    private fun store(cache: String, body: String, etag: String?) {
        runCatching {
            Disk.write(file(cache, "json"), body)
            val validator = file(cache, "etag")
            if (etag == null) Files.deleteIfExists(validator) else Disk.write(validator, etag)
        }.onFailure { Log.main.debug("Caching document '{}' failed: {}", cache, it.toString()) }
    }

    private fun parse(body: String): JsonObject? =
        runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()

    private fun file(cache: String, extension: String): Path =
        Platform.configDir.resolve("$cache.$extension").also { Files.createDirectories(it.parent) }

    private fun failed(cache: String, t: Throwable) {
        Log.main.warn("Applying document '{}' failed", cache, t)
    }
}
