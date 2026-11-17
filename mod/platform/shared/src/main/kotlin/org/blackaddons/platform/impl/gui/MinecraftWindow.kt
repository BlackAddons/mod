package org.blackaddons.platform.impl.gui
import net.minecraft.client.Minecraft
import org.blackaddons.api.gui.WindowMetrics

object MinecraftWindow : WindowMetrics {
    private val window get() = Minecraft.getInstance().window

    override val scaledWidth: Float
        get() = window.guiScaledWidth.toFloat()

    override val scaledHeight: Float
        get() = window.guiScaledHeight.toFloat()

    override val guiScale: Float
        get() = window.guiScale.toFloat()

    override val mouseX: Float
        get() = Minecraft.getInstance().mouseHandler.getScaledXPos(window).toFloat()

    override val mouseY: Float
        get() = Minecraft.getInstance().mouseHandler.getScaledYPos(window).toFloat()}
