package org.blackaddons.platform

import net.fabricmc.loader.api.FabricLoader
import org.blackaddons.api.PlatformProvider
import org.blackaddons.api.cosmetics.CosmeticHost
import org.blackaddons.api.hud.VanillaHud
import org.blackaddons.core.cosmetics.PlayerCosmetics
import org.blackaddons.core.platform.CoreHosts
import org.blackaddons.platform.impl.gui.MinecraftVanillaHud
import java.nio.file.Path

// Shared by every version's MinecraftPlatform. Initialized before the version's members, so facades can read these.
internal abstract class SharedPlatform : PlatformProvider {
    override val configDir: Path = FabricLoader.getInstance().configDir.resolve(Blackaddons.MOD_ID)
    override val gameDir: Path = FabricLoader.getInstance().gameDir
    override val blackaddonsVersion = loaderVersion(Blackaddons.MOD_ID)
    override val minecraftVersion = loaderVersion("minecraft")

    // Getter, because the resolver initializes on first use.
    override val cosmetics: CosmeticHost get() = PlayerCosmetics

    override val scheduler = CoreHosts.scheduler
    override val loggers = CoreHosts.loggers
    override val mojang = CoreHosts.mojang
    override val profiles = CoreHosts.profiles
    override val storage = CoreHosts.storage
    override val config = CoreHosts.config
    override val http = CoreHosts.http
    override val vanillaHud: VanillaHud = MinecraftVanillaHud

    // Fixed order: rotation and interaction run at HIGHEST on ClientTickEvent.Pre, aim has to settle before clicks age.
    fun install() = listOf(gameStats, containerOverlays, vanillaHud, movement, rotation, interaction)
        .filterIsInstance<Installable>()
        .forEach(Installable::install)

    private fun loaderVersion(modId: String): String = FabricLoader.getInstance()
        .getModContainer(modId)
        .map { it.metadata.version.friendlyString }
        .orElse(UNKNOWN_VERSION) ?: UNKNOWN_VERSION

    private companion object {
        const val UNKNOWN_VERSION = "unknown"
    }
}
