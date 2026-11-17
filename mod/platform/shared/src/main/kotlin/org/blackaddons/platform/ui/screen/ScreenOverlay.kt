package org.blackaddons.platform.ui.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.event.impl.RenderScreenEvent

/**
 * Draws over a covered screen and routes its input.
 * BLACKUM: I see potential!!!
 */
object ScreenOverlay {

    private var covered: Screen? = null

    fun start() {
        EventListener(RenderScreenEvent::class.java, EventPriority.LOWEST) { render(event.tickDelta) }.register()
    }

    fun cover(screen: Screen) {
        covered = screen
    }

    fun uncover() {
        covered = null
    }

    fun mouseButton(button: Int, action: Int): Boolean {
        if (!live()) return false
        val (x, y) = cursor()
        val mouse = MouseButtonInput(x.toFloat(), y.toFloat(), button)
        when (action) {
            Keys.PRESS -> UiManager.mouseClicked(mouse)
            Keys.RELEASE -> UiManager.mouseReleased(mouse)
        }
        return true
    }

    fun scroll(amount: Double): Boolean {
        if (!live()) return false
        val (x, y) = cursor()
        UiManager.mouseScrolled(MouseScrollInput(x.toFloat(), y.toFloat(), amount.toFloat()))
        return true
    }

    fun key(key: Int, action: Int, modifiers: Int): Boolean {
        if (!live()) return false
        if (action != Keys.RELEASE) UiManager.keyPressed(KeyInput(key, modifiers))
        return true
    }

    fun charTyped(codepoint: Int): Boolean {
        if (!live()) return false
        UiManager.charTyped(CharInput(codepoint.toChar(), 0))
        return true
    }

    private fun render(tickDelta: Float) {
        if (!live()) return
        val (x, y) = cursor()
        Render.beginPass()
        UiManager.render(x.toFloat(), y.toFloat(), tickDelta)
        UiManager.renderOverlay(x.toFloat(), y.toFloat())
    }

    private fun live(): Boolean {
        val screen = covered ?: return false
        val current = Minecraft.getInstance().currentScreen
        if (current === screen && UiManager.isOpen) return true
        covered = null
        if (current !is SkijaHostScreen) UiManager.close()
        return false
    }

    private fun cursor(): Pair<Double, Double> {
        val mc = Minecraft.getInstance()
        return mc.mouseHandler.getScaledXPos(mc.window) to mc.mouseHandler.getScaledYPos(mc.window)
    }
}
