package org.blackaddons.api.util

import java.security.MessageDigest
import java.util.HexFormat

object Hashes {

    /** SHA-256 over [parts] in order. */
    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
        parts.forEach(::update)
        digest()
    }

    /** Lowercase hex. */
    fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)

    /**
     * True for a lowercase SHA-256 digest as [hex] writes it. Check a hash with it before it goes into a path or a URL.
     */
    fun isSha256Hex(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
