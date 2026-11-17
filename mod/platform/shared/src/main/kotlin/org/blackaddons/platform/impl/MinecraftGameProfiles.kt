package org.blackaddons.platform.impl

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import org.blackaddons.core.profile.GameAccount
import org.blackaddons.core.profile.GameProfileLookup
import java.util.UUID

/**
 * Goes through the game's `ProfileResolver`, which uses the same lookups and caches as skulls and skins. By id it uses
 * the session profile, kept for 10 minutes. By name it checks `usercache.json` first, which is kept across runs.
 * Identical on every version.
 */
object MinecraftGameProfiles : GameProfileLookup {

    private const val TEXTURES = "textures"

    private val services get() = Minecraft.getInstance().services()
    private val resolver get() = services.profileResolver()

    override fun byName(name: String): GameAccount? = resolver.fetchByName(name).map(::account).orElse(null)

    override fun byUuid(uuid: UUID): GameAccount? = resolver.fetchById(uuid).map(::account).orElse(null)

    // A concurrent map in the game, so we can read it from any thread, and it never hits the network for an id.
    override fun knownName(uuid: UUID): String? = services.nameToIdCache().get(uuid).map { it.name() }.orElse(null)

    private fun account(profile: GameProfile): GameAccount {
        val textures = profile.properties()[TEXTURES].firstOrNull()
        return GameAccount(profile.id(), profile.name(), textures?.value(), textures?.signature())
    }
}
