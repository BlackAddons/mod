package org.blackaddons.platform.ui.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.blackskija.api.screen.SkijaScreen
import org.blackaddons.core.features.impl.settings.UiTheme
import org.blackaddons.platform.compat.openScreen
import org.blackaddons.platform.impl.gui.MinecraftPlayerModelPreview
import org.blackaddons.platform.impl.gui.MinecraftScreen
import org.blackaddons.platform.impl.input.toUi
import org.blackaddons.platform.impl.input.toUiDrag
import java.nio.file.Path

/**
 * The only Minecraft screen the mod hosts: renders [UiManager]'s top [Panel] and forwards input. Draws from
 * [extractRenderState], not BlackSkija's [draw], because draw() runs at composite time without mouse coordinates.
 */
class SkijaHostScreen(private val parent: Screen? = null) : SkijaScreen(Component.literal("BlackAddons")) {

    /**
     * In a world, no backdrop so the world shows. In the main menu that's plain black, so we borrow vanilla's panorama
     * and blur (vanilla's setting governs it there) and only drop the opaque tile ([extractMenuBackground]).
     */
    override val backdrop: Backdrop
        get() = when {
            Minecraft.getInstance().level == null -> Backdrop.VANILLA
            UiTheme.menuBlur.value -> Backdrop.BLUR
            else -> Backdrop.NONE
        }

    override val backdropDimTop: Int get() = 0
    override val backdropDimBottom: Int get() = 0

    /** Skips vanilla's opaque tile over the panorama, its blur stays. */
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor) = Unit

    // Drawn from extractRenderState instead.
    override fun draw() {}

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        Render.beginPass()
        UiManager.render(mouseX.toFloat(), mouseY.toFloat(), partialTick)
        // Models queued themselves, this just closes the frame.
        MinecraftPlayerModelPreview.endFrame()
        // Popups go over everything, so they get their own phase.
        UiManager.renderOverlay(mouseX.toFloat(), mouseY.toFloat())
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (UiManager.mouseClicked(click.toUi())) return true
        return super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        if (UiManager.mouseReleased(click.toUi())) return true
        return super.mouseReleased(click)
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (UiManager.mouseDragged(click.toUiDrag(deltaX, deltaY))) return true
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseScrolled(
        mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double,
    ): Boolean {
        if (UiManager.mouseScrolled(MouseScrollInput(mouseX.toFloat(), mouseY.toFloat(), verticalAmount.toFloat()))) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun init() {
        super.init()
        MinecraftScreen.resumeTextInput()
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (UiManager.keyPressed(input.toUi())) return true
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (UiManager.charTyped(input.toUi())) return true
        return super.charTyped(input)
    }

    override fun onFilesDrop(paths: List<Path>) {
        UiManager.filesDropped(paths)
    }

    /*
     * A server container close or another mod's setScreen skips onClose. A stale stack would stop push() from showing
     * the host again.
     */
    override fun removed() {
        super.removed()
        UiManager.onHostClosed()
    }

    override fun onClose() {
        if (parent != null) {
            Minecraft.getInstance().openScreen(parent)
        } else {
            super.onClose()
        }
    }
}
