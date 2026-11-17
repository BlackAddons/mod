package org.blackaddons.core.features.impl.ui

import org.blackaddons.api.Platform
import org.blackaddons.api.event.input.KeyInputEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.input.InputType
import org.blackaddons.api.player.HotbarBorrow
import org.blackaddons.core.features.impl.ui.HotbarKeybinds.holdToReturn
import org.blackaddons.core.features.impl.ui.HotbarKeybinds.release

/**
 * Extra hotbar binds next to the vanilla ones. A `KeyMapping` holds one input, so key and mouse need these. With
 * [holdToReturn] a bind is a peek: release goes back to the previous slot.
 */
internal object HotbarKeybinds : Feature(
    description = "Bind extra keys or mouse buttons to hotbar slots, on top of the vanilla ones.",
    name = "Hotbar Keybinds",
) {

    private val holdToReturn by boolean("Hold To Return", default = false)
        .withDescription("Hold the bind to bring the slot up, release to go back to the previous one.")

    // Declared one by one, because `by` registers them.
    private val slot1 by keybind("Slot 1")
    private val slot2 by keybind("Slot 2")
    private val slot3 by keybind("Slot 3")
    private val slot4 by keybind("Slot 4")
    private val slot5 by keybind("Slot 5")
    private val slot6 by keybind("Slot 6")
    private val slot7 by keybind("Slot 7")
    private val slot8 by keybind("Slot 8")
    private val slot9 by keybind("Slot 9")

    private val binds = listOf(slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9)

    private val peek = HotbarBorrow()

    override fun init() {
        register<KeyInputEvent> {
            if (event.isRepeat) return@register
            val slot = slotFor(InputType.KEYBOARD, event.key) ?: return@register
            if (event.isPress) press(slot) else release(slot)
        }

        register<MouseClickEvent> {
            val slot = slotFor(InputType.MOUSE, event.button) ?: return@register
            if (event.isPress) press(slot) else release(slot)
        }
    }

    override fun onDisable() {
        // Without super, listeners stay registered after disabling.
        super.onDisable()
        restore()
    }

    private fun slotFor(type: InputType, code: Int): Int? =
        binds.indexOfFirst { it.matches(type, code) }.takeIf { it >= 0 }

    /**
     * Presses only count with the world focused (not when typing digits in chat). [release] has no guard, or a screen
     * opened mid-peek would leave the player stuck.
     */
    private fun press(slot: Int) {
        if (Platform.screenHost.anyScreenOpen) return
        peek.take(slot, restore = holdToReturn.value)
    }

    private fun release(slot: Int) {
        if (!holdToReturn.value || peek.slot != slot) return
        restore()
    }

    private fun restore() = peek.release()
}
