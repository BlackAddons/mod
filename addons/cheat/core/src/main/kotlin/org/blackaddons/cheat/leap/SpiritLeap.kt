package org.blackaddons.cheat.leap

import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.dungeon.SpiritLeapEvent
import org.blackaddons.api.player.ClickRequest
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.ClientActionResult
import org.blackaddons.api.player.Hotbar
import org.blackaddons.api.player.HotbarBorrow
import org.blackaddons.api.skyblock.SkyblockItem
import org.blackaddons.api.skyblock.dungeon.SpiritLeapMenu

/**
 * Leaps to a teammate: leap in hand, right-click, click their head. The slot gets restored however it ends. Driven by
 * its owner's tick so it can't outlive it.
 */
class SpiritLeap {

    enum class Failure { NO_LEAP, MENU_NEVER_OPENED, NOT_IN_MENU }

    private enum class Stage { USE, MENU }

    private var target: String? = null
    private var onDone: (Failure?) -> Unit = {}
    private var stage = Stage.USE
    private var ticks = 0
    private var clickDelay = 0
    private var menuTicks = 0
    private var use: ClickRequest? = null
    private val hotbar = HotbarBorrow()

    val isActive: Boolean get() = target != null

    /** [clickDelay] is in ticks after the menu opens. [onDone] gets null on success, otherwise the failure. */
    fun start(name: String, clickDelay: Int = 0, onDone: (Failure?) -> Unit) {
        cancel()
        target = name
        this.clickDelay = clickDelay
        this.onDone = onDone
        EventBus.post(SpiritLeapEvent.Started(name))
    }

    fun tick() {
        val name = target ?: return
        if (++ticks > TIMEOUT_TICKS) return finish(if (menuOpen()) Failure.NOT_IN_MENU else Failure.MENU_NEVER_OPENED)
        when (stage) {
            Stage.USE -> useLeap()
            Stage.MENU -> pickHead(name)
        }
    }

    /** Never calls `onDone`. */
    fun cancel() {
        use?.cancel()
        use = null
        if (target != null) {
            hotbar.release()
            EventBus.post(SpiritLeapEvent.Ended)
        }
        target = null
        stage = Stage.USE
        ticks = 0
        menuTicks = 0
    }

    private fun useLeap() {
        if (use?.isPending == true) return
        val slot = Hotbar.find(preferred = hotbar.slot, match = SkyblockItem.SPIRIT_LEAP) ?: return finish(Failure.NO_LEAP)
        hotbar.take(slot)
        // Item use, not a key tap: the click shouldn't hit the stand or player in front.
        use = Platform.interaction.submit(ClientAction.UseItem, owner = this) { result ->
            if (result == ClientActionResult.SENT) stage = Stage.MENU
        }
    }

    private fun pickHead(name: String) {
        if (!menuOpen() || menuTicks++ < clickDelay) return
        // Heads load after the menu opens, so we wait until the timeout.
        val slot = SpiritLeapMenu.headSlot(name) ?: return
        Platform.container.clickSlot(slot, button = 0, quickMove = false)
        Platform.container.close()
        finish(null)
    }

    private fun menuOpen(): Boolean = SpiritLeapMenu.isOpen

    private fun finish(failure: Failure?) {
        if (failure == Failure.NOT_IN_MENU) Platform.container.close()
        val done = onDone
        cancel()
        done(failure)
    }

    private companion object {
        /** Right-click to head: menu opening, then heads loading. */
        const val TIMEOUT_TICKS = 40
    }
}
