package org.blackaddons.cheat.features.impl.cheats.autoRelic

import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.util.I18n
import org.blackaddons.cheat.leap.SpiritLeap
import java.awt.Color
import java.util.EnumMap

private val tr = I18n.scope("blackaddons.auto_relic")

/**
 * With a leap relic in the hotbar, leaps to whoever carries its [Relic.partner]. The carrier comes from the pickup chat
 * line, otherwise the usual class ([Relic.carrier]).
 */
internal class RelicLeap(private val alertColor: Color) {

    private val leap = SpiritLeap()
    private val pickers = EnumMap<Relic, String>(Relic::class.java)
    private var leaptFor: Relic? = null

    fun onChat(message: String) {
        val match = PICKUP.matchEntire(message) ?: return
        val relic = Relic.fromColor(match.groupValues[2]) ?: return
        pickers[relic] = match.groupValues[1]
    }

    /** Every client tick of the phase. [held] is null without a relic. */
    fun tick(held: Relic?) {
        leap.tick()
        val partner = held?.partner ?: return
        if (leaptFor == held) return
        leaptFor = held
        val name = pickers[partner] ?: carrierOf(partner)
        if (name == null) {
            Platform.chat.local(tr("leap_no_target", "Auto Relic: found nobody to leap to."), alertColor)
            return
        }
        leap.start(name) { failure -> failure?.let { Platform.chat.local(message(it, name), alertColor) } }
    }

    /** Keeps this phase's pickups. */
    fun stop() = leap.cancel()

    fun reset() {
        leap.cancel()
        pickers.clear()
        leaptFor = null
    }

    private fun carrierOf(relic: Relic): String? =
        Dungeon.team.others.firstOrNull { it.dungeonClass == relic.carrier && !it.isDead }?.name

    private fun message(failure: SpiritLeap.Failure, name: String): String = when (failure) {
        SpiritLeap.Failure.NO_LEAP -> tr("leap_none", "Auto Relic: no Spirit Leap in your hotbar.")
        SpiritLeap.Failure.MENU_NEVER_OPENED -> tr("leap_no_menu", "Auto Relic: the leap menu did not open.")
        SpiritLeap.Failure.NOT_IN_MENU -> tr("leap_not_in_menu", "Auto Relic: %s is not in the leap menu.", name)
    }

    private companion object {
        val PICKUP = Regex("""^(\w{3,16}) picked the Corrupted (\w{3,6}) Relic!$""")
    }
}
