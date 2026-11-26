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

/** Service locator for platform facades. Bound once at startup before any feature runs; reading earlier fails fast. */
object Platform {

    @Volatile
    private var bound: PlatformProvider? = null

    private val provider: PlatformProvider
        get() = bound ?: error("Platform was read before the host bound its PlatformProvider")

    /** Host only. A second call throws. */
    @HostApi
    fun bind(provider: PlatformProvider) {
        synchronized(this) {
            bound?.let { error("Platform is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    /** The player's selected language; translation is [org.blackaddons.api.util.I18n]'s job. */
    val locale: LocaleHost get() = provider.locale

    val window: WindowMetrics get() = provider.window

    val renderer: Renderer get() = provider.renderer

    val items: ItemHost get() = provider.items

    val container: OpenContainerHost get() = provider.container

    val screenDraw: ScreenDraw get() = provider.screenDraw

    val containerOverlays: ContainerOverlayRegistry get() = provider.containerOverlays

    val vanillaHud: VanillaHud get() = provider.vanillaHud

    /** e.g. `<game>/config/blackaddons`. */
    val configDir: Path get() = provider.configDir

    val gameDir: Path get() = provider.gameDir

    val gameStats: GameStats get() = provider.gameStats

    /** The single host screen the mod's UI renders inside. */
    val screenHost: ScreenHost get() = provider.screenHost

    /** Null on versions without one; callers draw a placeholder. */
    val playerPreview: PlayerModelPreview? get() = provider.playerPreview

    val cosmetics: CosmeticHost get() = provider.cosmetics

    val keyNames: KeyNames get() = provider.keyNames

    val blackaddonsVersion: String get() = provider.blackaddonsVersion

    val minecraftVersion: String get() = provider.minecraftVersion

    val chat: ChatHost get() = provider.chat

    val clipboard: ClipboardHost get() = provider.clipboard

    val desktop: DesktopHost get() = provider.desktop

    /** Keybinds in the vanilla Controls menu. */
    val keybinds: Keybinds get() = provider.keybinds

    val commands: CommandHost get() = provider.commands

    val player: PlayerHost get() = provider.player

    /** Read-only account status, safe for addons. The privileged session is first-party only and not exposed here. */
    val session: SessionStatus get() = provider.session

    val world: WorldHost get() = provider.world

    val camera: CameraHost get() = provider.camera

    val rotation: RotationHost get() = provider.rotation

    val movement: MovementHost get() = provider.movement

    val interaction: InteractionHost get() = provider.interaction

    val raycast: RaycastHost get() = provider.raycast

    val feedback: FeedbackHost get() = provider.feedback

    val skyblock: SkyblockHost get() = provider.skyblock

    /** Only effective inside `RenderWorldEvent`. */
    val worldRenderer: WorldRenderer get() = provider.worldRenderer

    val scheduler: Scheduler get() = provider.scheduler

    /**
     * The only facade that works before the host binds it. Unbound, it logs nothing, so an addon's own unit
     * tests can run its code.
     */
    val loggers: AddonLoggers get() = bound?.loggers ?: SilentLoggers

    val mojang: MojangAccounts get() = provider.mojang

    val profiles: SkyblockProfiles get() = provider.profiles

    val storage: AddonStorages get() = provider.storage

    /** For settings changed from code rather than the UI. */
    val config: ConfigHost get() = provider.config

    /** Blocking. Call it through [org.blackaddons.api.util.concurrent.OffThread]. */
    val http: HttpHost get() = provider.http

    val mods: ModsHost get() = provider.mods
}

private object SilentLoggers : AddonLoggers, AddonLogger {
    override fun of(addon: Addon): AddonLogger = this
    override fun info(message: String, vararg args: Any?) {}
    override fun warn(message: String, vararg args: Any?) {}
    override fun error(message: String, vararg args: Any?) {}
    override fun debug(message: String, vararg args: Any?) {}
    override fun area(area: String): AddonLogger = this
}
