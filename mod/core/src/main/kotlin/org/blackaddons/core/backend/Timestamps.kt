package org.blackaddons.core.backend

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Converts a backend timestamp to epoch ms. Accepts all three shapes the backend writes. A datetime without a zone is
 * UTC. Every reader goes through here. Null on failure, because each caller has its own fallback.
 */
internal object Timestamps {

    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
    }
}
