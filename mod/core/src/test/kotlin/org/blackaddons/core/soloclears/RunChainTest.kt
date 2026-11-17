package org.blackaddons.core.soloclears

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.util.Hashes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Checks [RunChain] against a separate fold like the backend's. Calling it twice would agree by construction. */
class RunChainTest {

    @Test
    fun `a chain is the server's fold over the same chunks`() {
        val chunks = listOf("first line\n", "second\nthird\n", "x").map { it.toByteArray() }

        var head = RunChain.EMPTY
        chunks.forEach { head = RunChain.link(head, it) }

        assertEquals(fold(chunks), Hashes.hex(head))
    }

    @Test
    fun `the head is sixty-four lowercase hex characters`() {
        val head = Hashes.hex(RunChain.link(RunChain.EMPTY, "anything".toByteArray()))

        assertEquals(64, head.length)
        assertTrue(head.all { it.isDigit() || it in 'a'..'f' }, head)
    }

    /**
     * The server folds the whole recording along the recorded chunk sizes, so committed chunks have to concatenate back
     * byte for byte.
     */
    @Test
    fun `the committed chunks concatenate back into the recording`() {
        val log = RunLog()
        val chunks = ArrayList<ByteArray>()

        repeat(3) { round ->
            repeat(4) { line -> log.append(record("track", round * 4L + line)) }
            chunks += log.take()!!
            log.commit()
        }

        assertEquals(log.size, chunks.sumOf { it.size })
        assertEquals(String(log.snapshot()), chunks.joinToString("") { String(it) })
    }

    @Test
    fun `a released chunk is offered again with what was written since`() {
        val log = RunLog()
        log.append(record("header", 0))

        val offered = log.take()!!
        log.release()
        log.append(record("track", 1))

        val second = log.take()!!
        assertTrue(second.size > offered.size)
        assertEquals(String(log.snapshot()), String(second))
    }

    @Test
    fun `nothing is offered while a chunk is in flight, or when nothing was written`() {
        val log = RunLog()
        assertNull(log.take())

        log.append(record("header", 0))
        assertNotNull(log.take())
        assertNull(log.take())

        log.commit()
        assertNull(log.take())
    }

    @Test
    fun `the upload is a gzip stream of the recording`() {
        val log = RunLog()
        log.append(record("header", 0))
        log.append(record("end", 900))

        val expanded = GZIPInputStream(ByteArrayInputStream(log.compressed())).readBytes()
        val lines = String(expanded).trim().lines()

        assertEquals(2, lines.size)
        assertEquals("header", JsonParser.parseString(lines[0]).asJsonObject.get("t").asString)
        assertEquals(900, JsonParser.parseString(lines[1]).asJsonObject.get("ms").asInt)
    }

    private fun record(type: String, offset: Long): JsonObject = JsonObject().apply {
        addProperty("t", type)
        addProperty("ms", offset)
    }

    /** The server's `RunChain.Fold`: `head = sha256(head || chunk)`, lowercase hex. */
    private fun fold(chunks: List<ByteArray>): String {
        var head = ByteArray(0)
        for (chunk in chunks) {
            val block = head + chunk
            head = MessageDigest.getInstance("SHA-256").digest(block)
        }
        return head.joinToString("") { "%02x".format(it) }
    }
}
