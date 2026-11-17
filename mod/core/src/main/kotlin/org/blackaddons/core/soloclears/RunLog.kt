package org.blackaddons.core.soloclears

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The recording: gzipped JSON Lines with the kind and ms since the dungeon timer. Text, because a run is only tens of
 * KB and readability matters in disputes. Client tick only, because committed bytes have to equal the file bytes.
 */
internal class RunLog {

    private val bytes = ByteArrayOutputStream(INITIAL_BYTES)

    /** Records since the last ack, in a separate buffer, so taking a chunk doesn't copy the whole recording. */
    private val chunk = ByteArrayOutputStream(CHUNK_BYTES)

    /** Kept, so a resend sends the same bytes. */
    private var inFlight: ByteArray? = null

    val size: Int get() = bytes.size()
    val overflowed: Boolean get() = size > MAX_BYTES

    fun append(record: JsonObject) {
        val line = record.toString().toByteArray(Charsets.UTF_8)
        bytes.write(line)
        bytes.write('\n'.code)
        chunk.write(line)
        chunk.write('\n'.code)
    }

    /** Null while one is in flight or nothing is new. Not committed until [commit]. Resends reuse this array. */
    fun take(): ByteArray? {
        if (inFlight != null || chunk.size() == 0) return null
        return chunk.toByteArray().also {
            inFlight = it
            chunk.reset()
        }
    }

    fun commit() {
        inFlight = null
    }

    /** Never sent. Its bytes go back to the front. */
    fun release() {
        val pending = inFlight ?: return
        val since = chunk.toByteArray()
        chunk.reset()
        chunk.write(pending)
        chunk.write(since)
        inFlight = null
    }

    fun snapshot(): ByteArray = bytes.toByteArray()

    /** Raw gzip stream, not multipart. */
    fun compressed(): ByteArray {
        val out = ByteArrayOutputStream(size / 4 + 1024)
        GZIPOutputStream(out).use { it.write(bytes.toByteArray()) }
        return out.toByteArray()
    }

    companion object {

        private const val INITIAL_BYTES = 128 * 1024
        private const val CHUNK_BYTES = 8 * 1024

        /** Well under the backend's limit. Past this, something is looping. */
        private const val MAX_BYTES = 6 * 1024 * 1024
    }
}

internal fun JsonObject.addStrings(key: String, values: List<String>) {
    if (values.isEmpty()) return
    add(key, JsonArray().apply { values.forEach(::add) })
}

internal object RunRecords {
    const val HEADER = "header"
    const val SCORE = "score"
    const val ROOM = "room"
    const val DOOR = "door"
    const val KEY = "key"
    const val SECRET = "secret"
    const val SECRET_SKIP = "secret_skip"
    const val DEATH = "death"
    const val BONUS = "bonus"
    const val BOSS = "boss"
    const val TRACK = "track"
    const val MAP = "map"
    const val SPLIT = "split"
    const val END = "end"
}
