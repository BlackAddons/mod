package org.blackaddons.core.dungeon

import org.blackaddons.core.skyblock.CoreElection

/* Paul's EZPZ perk, from the mayor or the minister, adds 10 to the dungeon score. */
internal object PaulBonus {

    private const val PERK = "EZPZ"

    internal fun active(forceEnable: Boolean, forceDisable: Boolean): Boolean =
        !forceDisable && (forceEnable || CoreElection.mayor?.hasPerk(PERK) == true)
}
