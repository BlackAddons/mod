package org.blackaddons.api.skyblock

import org.blackaddons.api.HostApi

/** A perk, named the way Hypixel names it (`"EZPZ"`). [description] keeps its color codes. */
data class MayorPerk(val name: String, val description: String)

/** The minister: the runner-up of the election, with one of their perks. */
data class Minister(val key: String, val name: String, val perk: MayorPerk)

/** The mayor in office. [key] is Hypixel's id (`"paul"`) and [name] is the display name (`"Paul"`). */
data class Mayor(val key: String, val name: String, val perks: List<MayorPerk>, val minister: Minister?) {

    /** Every perk in effect, both the mayor's and the minister's. */
    val activePerks: List<MayorPerk> get() = perks + listOfNotNull(minister?.perk)

    /** True when the mayor or the minister has [perk], by name, ignoring case. */
    fun hasPerk(perk: String): Boolean = activePerks.any { it.name.equals(perk, ignoreCase = true) }
}

/**
 * The SkyBlock mayor, from Hypixel's election resource. It is fetched at startup, at each dungeon run start, and on a
 * world change at most once an hour. Null until the first answer arrives.
 */
object Election {

    @Volatile
    private var bound: ElectionProvider? = null

    private val provider: ElectionProvider
        get() = bound ?: error("Election was read before the host bound its provider")

    /** Host only. */
    @HostApi
    fun bind(provider: ElectionProvider) {
        synchronized(this) {
            bound?.let { error("Election is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    val mayor: Mayor? get() = provider.mayor

    /** [Mayor.hasPerk] for the current mayor. False while the mayor is unknown. */
    fun hasPerk(perk: String): Boolean = mayor?.hasPerk(perk) == true
}

/** Host side of [Election], bound once. */
interface ElectionProvider {
    val mayor: Mayor?
}
