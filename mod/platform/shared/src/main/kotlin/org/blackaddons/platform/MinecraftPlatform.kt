package org.blackaddons.platform

import org.blackaddons.platform.impl.FabricMods
import org.blackaddons.platform.impl.MinecraftClipboard
import org.blackaddons.platform.impl.MinecraftDesktop
import org.blackaddons.platform.impl.MinecraftGameStats
import org.blackaddons.platform.impl.MinecraftLocale
import org.blackaddons.platform.impl.MinecraftSessionStatus
import org.blackaddons.platform.impl.MinecraftSkyblock
import org.blackaddons.platform.impl.chat.MinecraftChat
import org.blackaddons.platform.impl.chat.MinecraftFeedback
import org.blackaddons.platform.impl.command.MinecraftCommands
import org.blackaddons.platform.impl.gui.MinecraftContainerOverlays
import org.blackaddons.platform.impl.gui.MinecraftOpenContainer
import org.blackaddons.platform.impl.gui.MinecraftPlayerModelPreview
import org.blackaddons.platform.impl.gui.MinecraftScreen
import org.blackaddons.platform.impl.gui.MinecraftScreenDraw
import org.blackaddons.platform.impl.gui.MinecraftWindow
import org.blackaddons.platform.impl.input.MinecraftKeyNames
import org.blackaddons.platform.impl.input.MinecraftKeybinds
import org.blackaddons.platform.impl.player.MinecraftCamera
import org.blackaddons.platform.impl.player.MinecraftInteraction
import org.blackaddons.platform.impl.player.MinecraftMovement
import org.blackaddons.platform.impl.player.MinecraftPlayer
import org.blackaddons.platform.impl.player.MinecraftRaycast
import org.blackaddons.platform.impl.player.rotation.MinecraftRotation
import org.blackaddons.platform.impl.world.MinecraftItems
import org.blackaddons.platform.impl.world.MinecraftWorld
import org.blackaddons.platform.impl.world.MinecraftWorldRenderer
import org.blackaddons.platform.ui.render.SkijaRenderer

internal object MinecraftPlatform : SharedPlatform() {
    override val locale = MinecraftLocale
    override val window = MinecraftWindow
    override val renderer = SkijaRenderer
    override val items = MinecraftItems
    override val container = MinecraftOpenContainer
    override val screenDraw = MinecraftScreenDraw
    override val containerOverlays = MinecraftContainerOverlays
    override val gameStats = MinecraftGameStats
    override val screenHost = MinecraftScreen
    override val keyNames = MinecraftKeyNames
    override val chat = MinecraftChat
    override val clipboard = MinecraftClipboard
    override val desktop = MinecraftDesktop
    override val keybinds = MinecraftKeybinds
    override val commands = MinecraftCommands
    override val player = MinecraftPlayer
    override val session = MinecraftSessionStatus
    override val world = MinecraftWorld
    override val camera = MinecraftCamera
    override val rotation = MinecraftRotation
    override val movement = MinecraftMovement
    override val interaction = MinecraftInteraction
    override val raycast = MinecraftRaycast
    override val feedback = MinecraftFeedback
    override val skyblock = MinecraftSkyblock
    override val worldRenderer = MinecraftWorldRenderer
    override val mods = FabricMods
    override val playerPreview = MinecraftPlayerModelPreview
}
