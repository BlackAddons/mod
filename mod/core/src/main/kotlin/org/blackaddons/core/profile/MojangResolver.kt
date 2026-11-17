package org.blackaddons.core.profile

import com.google.gson.JsonParser
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log
import java.util.UUID

internal data class MojangId(val uuid: String, val name: String)

/**
 * Mojang lookups. The game's own lookup ([HostGameProfiles]) from the player's connection comes first. When the game
 * finds nothing, we ask our backend's cached proxy, which is also how "no such player" gets told apart from "Mojang is
 * down". The proxy wants our session, so other mods can't resolve through us. Without a session it reads as
 * unavailable. Blocking.
 */
internal object MojangResolver {

    fun resolve(name: String): Resolution =
        fromGame { byName(name) } ?: profile("${Backend.MOJANG_UUID_URL}/$name", name)

    /** The current name of [uuid]. */
    fun nameOf(uuid: UUID): Resolution =
        fromGame { byUuid(uuid) } ?: profile("${Backend.MOJANG_USERNAME_URL}/$uuid", uuid.toString())

    /** A name we know without a request: seen this session, or in the game's own name cache. */
    fun knownName(uuid: UUID): String? =
        PlayerNames.nameOf(uuid) ?: runCatching { HostGameProfiles.lookup?.knownName(uuid) }.getOrNull()

    private fun fromGame(lookup: GameProfileLookup.() -> GameAccount?): Resolution.Found? =
        game(lookup)?.let { Resolution.Found(MojangId(it.uuid.dashless, it.name)) }

    // The game's cache loaders throw when a fetch fails. Here that's a miss, and the backend gets its turn.
    private fun game(lookup: GameProfileLookup.() -> GameAccount?): GameAccount? =
        runCatching { HostGameProfiles.lookup?.lookup() }.getOrNull()

    private fun profile(url: String, name: String): Resolution {
        val response = Http.get(url, authenticated = true) ?: return Resolution.Unavailable
        return when (response.status) {
            200 -> runCatching {
                val obj = JsonParser.parseString(response.body).asJsonObject
                val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return Resolution.NotFound
                val canonical = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: name
                Resolution.Found(MojangId(id.replace("-", "").lowercase(), canonical))
            }.getOrElse { Resolution.Unavailable }
            // 400 means a name Mojang could never issue.
            400, 404 -> Resolution.NotFound
            else -> {
                // Not "not found": a 502 must not claim a real account doesn't exist.
                Log.main.warn("Resolve '{}' failed: HTTP {}", name, response.status)
                Resolution.Unavailable
            }
        }
    }

    sealed interface Resolution {
        data class Found(val id: MojangId) : Resolution
        object NotFound : Resolution
        object Unavailable : Resolution
    }

    /** The signed `textures` property, or null. Other players' skins need the signature. */
    fun texturesProperty(uuid: String): SkinTextures? {
        val account = Uuids.parse(uuid)?.let { id -> game { byUuid(id) } }
        account?.textures?.let { return SkinTextures(it, account.signature) }
        val response = Http.get("${Backend.MOJANG_PROFILE_URL}/$uuid", authenticated = true) ?: return null
        if (response.status != 200) {
            Log.main.warn("Skin lookup for {} failed: HTTP {}", uuid, response.status)
            return null
        }
        return runCatching {
            val prop = JsonParser.parseString(response.body).asJsonObject.getAsJsonArray("properties")
                ?.firstNotNullOfOrNull { it.asJsonObject.takeIf { p -> p.get("name")?.asString == "textures" } }
            val value = prop?.get("value")?.asString ?: return@runCatching null
            SkinTextures(value, prop.get("signature")?.takeIf { !it.isJsonNull }?.asString)
        }.getOrNull()
    }
}
