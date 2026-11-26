package org.blackaddons.platform.impl.gui

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.render.DisconnectConfirmationEvent
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.I18n
import org.blackaddons.platform.event.impl.RenderScreenEvent
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Asks before the pause screen's Disconnect leaves a server. Vanilla's button stays, hidden, and still does the leaving
 * (draft reports, other mods' hooks): a stand-in at its spot opens the card, and the card's Disconnect hands its press on
 * to vanilla's.
 */
internal object PauseDisconnect {

    private const val DISCONNECT_KEY = "menu.disconnect"

    private val tr = I18n.scope("blackaddons.pause_menu")
    private val card = ConfirmCard()
    private var screen: PauseScreen? = null
    private var vanilla: Button? = null
    private var standIn: Button? = null
    private var choices = emptyList<ConfirmCard.Choice>()
    private var open = false

    private val owned: MutableSet<AbstractWidget> = Collections.newSetFromMap(IdentityHashMap())

    fun install() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            reset()
            if (screen is PauseScreen && screen.showsPauseMenu() && !Minecraft.getInstance().isLocalServer &&
                EventBus.post(DisconnectConfirmationEvent()).confirm
            ) {
                wrap(screen)
            }
        }
        EventListener(RenderScreenEvent::class.java, EventPriority.HIGH) {
            if (event.screen === screen) draw()
        }.register()
    }

    @JvmStatic
    fun owns(widget: AbstractWidget): Boolean = widget in owned

    private fun reset() {
        screen = null
        vanilla = null
        standIn = null
        choices = emptyList()
        open = false
        owned.clear()
    }

    private fun wrap(screen: PauseScreen) {
        val widgets = Screens.getWidgets(screen)
        val disconnect = widgets.filterIsInstance<Button>()
            .firstOrNull { (it.message.contents as? TranslatableContents)?.key == DISCONNECT_KEY } ?: return
        val trigger = Button.builder(disconnect.message) { open = !open }
            .bounds(disconnect.x, disconnect.y, disconnect.width, disconnect.height).build()
        val cancel = Button.builder(CommonComponents.GUI_CANCEL) { open = false }.build()
        val leave = Forward(disconnect.message, disconnect)
        disconnect.visible = false
        cancel.visible = false
        leave.visible = false
        widgets += trigger
        widgets += cancel
        widgets += leave
        choices = listOf(
            ConfirmCard.Choice(cancel, cancel.message.string),
            ConfirmCard.Choice(leave, leave.message.string, danger = true),
        )
        owned += cancel
        owned += leave
        this.screen = screen
        vanilla = disconnect
        standIn = trigger
    }

    private fun draw() {
        val screen = screen ?: return
        val disconnect = vanilla ?: return
        val trigger = standIn ?: return
        trigger.setPosition(disconnect.x, disconnect.y)
        trigger.setSize(disconnect.width, disconnect.height)
        card.layout(open, trigger, tr("confirm_disconnect", "Leave the server?"), choices, screen.width, screen.height, UiManager.normalizedScale)
        card.draw()
    }

    private class Forward(message: Component, private val target: Button) : Button.Plain(0, 0, 0, 0, message, Button.OnPress {}, Button.CreateNarration { it.get() }) {
        override fun onPress(input: InputWithModifiers) {
            active = false
            target.onPress(input)
        }
    }
}
