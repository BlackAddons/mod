package org.blackaddons.api.util

import java.util.UUID

/** UUID parsing that accepts Mojang's dash-less form, which [UUID.fromString] rejects. */
object Uuids {

    /** Dashed or 32-hex; null for blank or malformed input. */
    fun parse(raw: String?): UUID? {
        val s = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val dashed = when {
            '-' in s -> s
            s.length == 32 -> "${s.substring(0, 8)}-${s.substring(8, 12)}-${s.substring(12, 16)}-" +
                "${s.substring(16, 20)}-${s.substring(20)}"
            else -> return null
        }
        return runCatching { UUID.fromString(dashed) }.getOrNull()
    }

    /**
     * True only for v4 (Mojang-issued) ids. Hypixel's tab fillers, NPCs and dungeon fake players use v3/v2,
     * and can have perfectly valid names (e.g. "Murz474"). Check this before spending anything per player.
     * Trick from NoammAddons (CC0).
     */
    fun isAccount(uuid: UUID?): Boolean = uuid != null && uuid.version() == 4
}
