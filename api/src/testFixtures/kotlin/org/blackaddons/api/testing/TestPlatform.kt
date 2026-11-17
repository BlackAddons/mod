package org.blackaddons.api.testing

import org.blackaddons.api.Platform
import org.blackaddons.api.PlatformProvider
import org.blackaddons.api.camera.CameraHost
import org.blackaddons.api.chat.ChatHost
import org.blackaddons.api.chat.FeedbackHost
import org.blackaddons.api.commands.CommandHost
import org.blackaddons.api.config.ConfigHost
import org.blackaddons.api.cosmetics.CosmeticHost
import org.blackaddons.api.gui.ContainerOverlayRegistry
import org.blackaddons.api.gui.OpenContainerHost
import org.blackaddons.api.gui.PlayerModelPreview
import org.blackaddons.api.gui.ScreenDraw
import org.blackaddons.api.gui.ScreenHost
import org.blackaddons.api.gui.WindowMetrics
import org.blackaddons.api.host.*
import org.blackaddons.api.hud.VanillaHud
import org.blackaddons.api.input.KeyNames
import org.blackaddons.api.input.Keybinds
import org.blackaddons.api.player.InteractionHost
import org.blackaddons.api.player.MovementHost
import org.blackaddons.api.player.PlayerHost
import org.blackaddons.api.player.RaycastHost
import org.blackaddons.api.player.RotationHost
import org.blackaddons.api.skyblock.SkyblockHost
import org.blackaddons.api.skyblock.profile.SkyblockProfiles
import org.blackaddons.api.ui.render.Renderer
import org.blackaddons.api.world.ItemHost
import org.blackaddons.api.world.WorldHost
import org.blackaddons.api.world.WorldRenderer
import java.nio.file.Path

/** Test [PlatformProvider], one settable field per facade. Bound lazily; unset ones throw like unbound [Platform]. */
object TestPlatform : PlatformProvider {
    override lateinit var locale: LocaleHost
    override lateinit var window: WindowMetrics
    override lateinit var renderer: Renderer
    override lateinit var items: ItemHost
    override lateinit var container: OpenContainerHost
    override lateinit var screenDraw: ScreenDraw
    override lateinit var containerOverlays: ContainerOverlayRegistry
    override lateinit var vanillaHud: VanillaHud
    override lateinit var configDir: Path
    override lateinit var gameDir: Path
    override lateinit var gameStats: GameStats
    override lateinit var screenHost: ScreenHost
    override var cosmetics: CosmeticHost = CosmeticHost.None
    override lateinit var keyNames: KeyNames
    override var blackaddonsVersion = "test"
    override var minecraftVersion = "test"
    override lateinit var chat: ChatHost
    override lateinit var clipboard: ClipboardHost
    override lateinit var desktop: DesktopHost
    override lateinit var keybinds: Keybinds
    override lateinit var commands: CommandHost
    override lateinit var player: PlayerHost
    override lateinit var session: SessionStatus
    override lateinit var world: WorldHost
    override lateinit var camera: CameraHost
    override lateinit var rotation: RotationHost
    override lateinit var movement: MovementHost
    override lateinit var interaction: InteractionHost
    override lateinit var raycast: RaycastHost
    override lateinit var feedback: FeedbackHost
    override lateinit var skyblock: SkyblockHost
    override lateinit var worldRenderer: WorldRenderer
    override lateinit var scheduler: Scheduler
    override var loggers: AddonLoggers = Platform.loggers
    override lateinit var mojang: MojangAccounts
    override lateinit var profiles: SkyblockProfiles
    override lateinit var storage: AddonStorages
    override lateinit var config: ConfigHost
    override lateinit var http: HttpHost
    override lateinit var mods: ModsHost
    override var playerPreview: PlayerModelPreview? = null

    init {
        Platform.bind(this)
    }
}

