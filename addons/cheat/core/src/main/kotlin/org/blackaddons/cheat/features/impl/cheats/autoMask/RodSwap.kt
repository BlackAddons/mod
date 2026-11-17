package org.blackaddons.cheat.features.impl.cheats.autoMask

import org.blackaddons.api.Platform
import org.blackaddons.api.player.ClickRequest
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.ClientActionResult
import org.blackaddons.api.player.Hotbar
import org.blackaddons.api.player.HotbarBorrow
import org.blackaddons.api.world.ItemInfo

internal class RodSwap(private val owner: Any) {

    enum class Failure { NO_ROD, NOT_SENT }

    private enum class Stage { CAST, RECAST_WAIT, RECAST, RELEASE }

    private val hotbar = HotbarBorrow()
    private var stage: Stage? = null
    private var recast = false
    private var waitTicks = 0
    private var use: ClickRequest? = null
    private var onDone: (Failure?) -> Unit = {}

    val isActive: Boolean get() = stage != null

    fun start(onDone: (Failure?) -> Unit) {
        cancel()
        val slot = Hotbar.find(match = ::isSwapRod) ?: return onDone(Failure.NO_ROD)
        this.onDone = onDone
        hotbar.take(slot)
        recast = Platform.player.isFishing
        stage = Stage.CAST
    }

    fun tick() {
        when (stage ?: return) {
            Stage.CAST -> cast(if (recast) Stage.RECAST_WAIT else Stage.RELEASE)
            Stage.RECAST_WAIT -> if (++waitTicks >= RECAST_DELAY_TICKS) stage = Stage.RECAST
            Stage.RECAST -> cast(Stage.RELEASE)
            Stage.RELEASE -> finish(null)
        }
    }

    fun cancel() {
        use?.cancel()
        use = null
        if (stage != null) hotbar.release()
        stage = null
        waitTicks = 0
    }

    private fun cast(next: Stage) {
        if (use?.isPending == true) return
        use = Platform.interaction.submit(ClientAction.UseItem, owner = owner) { result ->
            use = null
            if (result == ClientActionResult.SENT) stage = next else finish(Failure.NOT_SENT)
        }
    }

    private fun finish(failure: Failure?) {
        val done = onDone
        cancel()
        done(failure)
    }

    private fun isSwapRod(item: ItemInfo): Boolean = item.id == FISHING_ROD && item.skyblockId !in ROD_BLACKLIST

    private companion object {
        const val FISHING_ROD = "minecraft:fishing_rod"
        const val RECAST_DELAY_TICKS = 4

        val ROD_BLACKLIST = setOf("SOUL_WHIP", "FLAMING_FLAY", "GRAPPLING_HOOK")
    }
}
