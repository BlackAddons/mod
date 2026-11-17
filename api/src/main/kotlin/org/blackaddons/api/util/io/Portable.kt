package org.blackaddons.api.util.io
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

sealed interface PortableRead {

    /** The caller checks [kind]. */
    data class Document(val kind: String, val json: JsonObject) : PortableRead

    data object Empty : PortableRead

    /** Not one of our strings. */
    data object NotPortable : PortableRead

    /** Written by a newer version. */
    data class NewerFormat(val version: Int) : PortableRead

    /** Ours, but truncated. */
    data object Corrupt : PortableRead

    /** Ours, but too big to be a config. */
    data object TooLarge : PortableRead
}

/**
 * Configs as a shareable string: `BLACKADDONS<version>.<kind>.<payload>` (deflated JSON, url-safe base64).
 * The version detects format changes; the kind lets an importer say "that's a theme". Not `BA` - another mod
 * uses it.
 */
object Portable {

    private const val PREFIX = "BLACKADDONS"
    private const val VERSION = 1

    // Loose enough for any real config, tight enough to stop a decompression bomb.
    private const val MAX_TEXT = 4 * 1024 * 1024
    private const val MAX_DOCUMENT = 1024 * 1024

    private val KIND = Regex("[a-z][a-z0-9-]{0,31}")

    /** e.g. `theme`. */
    fun encode(kind: String, json: JsonObject): String {
        require(KIND.matches(kind)) { "kind must be lowercase, got '$kind'" }
        val payload = deflate(json.toString().toByteArray())
        return "$PREFIX$VERSION.$kind.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload)}"
    }

    /** Whitespace is dropped first, so chat-wrapped strings still read. */
    fun decode(text: String): PortableRead {
        val clean = text.filterNot(Char::isWhitespace)
        if (clean.isEmpty()) return PortableRead.Empty
        if (clean.length > MAX_TEXT) return PortableRead.TooLarge

        val parts = clean.split('.')
        if (parts.size != 3) return PortableRead.NotPortable
        val (header, kind, payload) = parts

        if (!header.startsWith(PREFIX)) return PortableRead.NotPortable
        val version = header.removePrefix(PREFIX).toIntOrNull()?.takeIf { it > 0 }
            ?: return PortableRead.NotPortable
        if (version > VERSION) return PortableRead.NewerFormat(version)
        if (!KIND.matches(kind)) return PortableRead.NotPortable

        val bytes = runCatching { Base64.getUrlDecoder().decode(payload) }.getOrNull()
            ?: return PortableRead.NotPortable
        val document = when (val inflated = inflate(bytes)) {
            Inflated.Broken -> return PortableRead.Corrupt
            Inflated.TooLarge -> return PortableRead.TooLarge
            is Inflated.Bytes -> inflated.value
        }
        val json = runCatching { JsonParser.parseString(document.decodeToString()) }.getOrNull()
        return if (json != null && json.isJsonObject) {
            PortableRead.Document(kind, json.asJsonObject)
        } else {
            PortableRead.Corrupt
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size / 2)
            val buffer = ByteArray(BUFFER)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(bytes: ByteArray): Inflated {
        val inflater = Inflater()
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream(bytes.size * 2)
            val buffer = ByteArray(BUFFER)
            while (!inflater.finished()) {
                val read = try {
                    inflater.inflate(buffer)
                } catch (_: DataFormatException) {
                    return Inflated.Broken
                }
                // Input ran out before the stream ended: truncated.
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) return Inflated.Broken
                out.write(buffer, 0, read)
                if (out.size() > MAX_DOCUMENT) return Inflated.TooLarge
            }
            return Inflated.Bytes(out.toByteArray())
        } finally {
            inflater.end()
        }
    }

    private const val BUFFER = 8 * 1024

    private sealed interface Inflated {
        class Bytes(val value: ByteArray) : Inflated
        data object Broken : Inflated
        data object TooLarge : Inflated
    }
}
