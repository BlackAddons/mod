package org.blackaddons.api

import org.blackaddons.api.camera.CameraHost
import org.blackaddons.api.chat.ChatHost
import org.blackaddons.api.chat.FeedbackHost
import org.blackaddons.api.commands.CommandHost
import org.blackaddons.api.config.ConfigHost
import org.blackaddons.api.cosmetics.CosmeticHost
import org.blackaddons.api.gui.*
import org.blackaddons.api.host.*
import org.blackaddons.api.hud.VanillaHud
import org.blackaddons.api.input.KeyNames
import org.blackaddons.api.input.Keybinds
import org.blackaddons.api.player.*
import org.blackaddons.api.skyblock.SkyblockHost
import org.blackaddons.api.skyblock.profile.SkyblockProfiles
import org.blackaddons.api.ui.render.Renderer
import org.blackaddons.api.world.ItemHost
import org.blackaddons.api.world.WorldHost
import org.blackaddons.api.world.WorldRenderer
import java.nio.file.Path

/**
 * Everything [Platform] hands out. Each version implements it as one `MinecraftPlatform` object, so a missing
 * facade is a compile error. The Mojang session is deliberately absent.
 */
interface PlatformProvider {
    val locale: LocaleHost
    val window: WindowMetrics
    val renderer: Renderer
    val items: ItemHost
    val container: OpenContainerHost
    val screenDraw: ScreenDraw
    val containerOverlays: ContainerOverlayRegistry
    val vanillaHud: VanillaHud
    val configDir: Path
    val gameDir: Path
    val gameStats: GameStats
    val screenHost: ScreenHost
    val cosmetics: CosmeticHost
    val keyNames: KeyNames
    val blackaddonsVersion: String
    val minecraftVersion: String
    val chat: ChatHost
    val clipboard: ClipboardHost
    val desktop: DesktopHost
    val keybinds: Keybinds
    val commands: CommandHost
    val player: PlayerHost
    val session: SessionStatus
    val world: WorldHost
    val camera: CameraHost
    val rotation: RotationHost
    val movement: MovementHost
    val interaction: InteractionHost
    val raycast: RaycastHost
    val feedback: FeedbackHost
    val skyblock: SkyblockHost
    val worldRenderer: WorldRenderer
    val scheduler: Scheduler
    val loggers: AddonLoggers
    val mojang: MojangAccounts
    val profiles: SkyblockProfiles
    val storage: AddonStorages
    val config: ConfigHost
    val http: HttpHost
    val mods: ModsHost

    val playerPreview: PlayerModelPreview? get() = null
}
