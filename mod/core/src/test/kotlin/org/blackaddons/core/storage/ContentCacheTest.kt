package org.blackaddons.core.storage

import org.blackaddons.api.host.HttpHost
import org.blackaddons.api.testing.TestPlatform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest

/**
 * Hash-keyed content only gets fetched once. Every call gets an unresolvable URL, so a test that downloads fails by
 * coming back empty. Access also has to touch mtime, the only thing prune sorts by.
 */
class ContentCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private val payload = "a picture, more or less".toByteArray()
    private val hash = sha256(payload)
    private val cache = ContentCache(DIR, 1024L * 1024L)

    @BeforeEach
    fun setUp() {
        TestPlatform.configDir = tempDir
        TestPlatform.http = unreachableHttp
    }

    @Test
    fun `content already on disk is served without a fetch`() {
        store(payload)
        assertArrayEquals(payload, cache.bytes(hash, UNREACHABLE))
    }

    @Test
    fun `the path of a hit is the file itself`() {
        val file = store(payload)
        assertEquals(file, cache.path(hash, UNREACHABLE))
    }

    @Test
    fun `reading a hit records the access, which is what prune sorts by`() {
        val file = store(payload)
        val longAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        Files.setLastModifiedTime(file, FileTime.fromMillis(longAgo))

        cache.bytes(hash, UNREACHABLE)

        assertTrue(Files.getLastModifiedTime(file).toMillis() > longAgo) {
            "an untouched file would be evicted for having been written long ago, not for being unwanted"
        }
    }

    @Test
    fun `a miss that cannot be fetched is an absence, not a failure`() {
        assertEquals(null, cache.bytes(hash, UNREACHABLE))
        assertEquals(null, cache.path(hash, UNREACHABLE))
    }

    @Test
    fun `trusted bytes are stored under their computed hash`() {
        assertEquals(hash, cache.put(payload))
        assertArrayEquals(payload, Files.readAllBytes(cache.path(hash)))
    }

    @Test
    fun `cached content can be removed without accepting unsafe keys`() {
        val file = store(payload)

        assertTrue(cache.remove(hash))
        assertFalse(Files.exists(file))
        assertFalse(cache.remove("../blackaddons.json"))
    }

    @Test
    fun `cache keys cannot escape the cache directory`() {
        assertNull(cache.path("../blackaddons.json"))
    }

    private fun store(data: ByteArray): Path {
        val dir = tempDir.resolve(DIR)
        Files.createDirectories(dir)
        return dir.resolve(hash).also { Files.write(it, data) }
    }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private companion object {
        val unreachableHttp = object : HttpHost {
            override fun send(
                url: String,
                method: HttpHost.Method,
                body: String?,
                contentType: String?,
                headers: Map<String, String>,
                ifNoneMatch: String?,
                onResult: (HttpHost.Response?) -> Unit,
            ) = onResult(null)

            override fun bytes(url: String, onResult: (ByteArray?) -> Unit) = onResult(null)

            override fun sendBlocking(
                url: String,
                method: HttpHost.Method,
                body: String?,
                contentType: String?,
                headers: Map<String, String>,
                ifNoneMatch: String?,
            ): HttpHost.Response? = null

            override fun bytesBlocking(url: String): ByteArray? = null
        }

        const val DIR = "test-cache"
        const val UNREACHABLE = "https://cache-must-not-fetch.invalid/nope"
    }
}
