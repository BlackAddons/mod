package org.blackaddons.core.profile

import com.google.gson.JsonParser
import org.blackaddons.api.util.io.bool
import org.blackaddons.api.util.io.child
import org.blackaddons.api.util.io.str
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.gamedata.SkyblockAreas
import org.blackaddons.core.log.Log

internal sealed interface PlayerStatus {
    data class Online(val area: String) : PlayerStatus

    /** Offline and "status API off" look the same, so the UI claims neither. */
    object Hidden : PlayerStatus

    object Unavailable : PlayerStatus
}

/** Hypixel `/v2/status` through the proxy, for Profile Viewer. Never cached, location goes stale in seconds. Blocking. */
internal object PlayerStatusSource {

    fun fetch(uuid: String): PlayerStatus {
        val response = Http.get("${Backend.HYPIXEL_URL}/v2/status?uuid=$uuid", authenticated = true)
            ?: return PlayerStatus.Unavailable
        if (response.status != 200) {
            Log.main.warn("Status lookup for {} unavailable: backend status {}", uuid, response.status)
            return PlayerStatus.Unavailable
        }
        return runCatching { parse(response.body) }.getOrElse {
            Log.main.warn("Status lookup for {} unavailable: response parse failed", uuid, it)
            PlayerStatus.Unavailable
        }
    }

    internal fun parse(body: String): PlayerStatus {
        val session = JsonParser.parseString(body).asJsonObject.child("session")
        if (!session.bool("online")) return PlayerStatus.Hidden
        // `mode` is the island id Skyblock sends. [SkyblockAreas] names it (`dynamic` -> "Private Island").
        val mode = session.str("mode")
        val area = mode?.let(SkyblockAreas::name) ?: session.str("map")
        return if (area.isNullOrEmpty()) PlayerStatus.Hidden else PlayerStatus.Online(area)
    }
}
