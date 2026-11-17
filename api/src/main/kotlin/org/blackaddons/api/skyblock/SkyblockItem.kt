package org.blackaddons.api.skyblock

import org.blackaddons.api.util.cleanName
import org.blackaddons.api.world.ItemInfo

/**
 * Matches by id, or by name only when the stack has no id (menu icons, other servers). Use as a predicate:
 * `Hotbar.find(match = SkyblockItem.SPIRIT_LEAP)`.
 */
class SkyblockItem(private val ids: Set<String>, private val names: List<String>) : (ItemInfo) -> Boolean {

    constructor(id: String, name: String) : this(setOf(id), listOf(name))

    override fun invoke(item: ItemInfo): Boolean {
        item.skyblockId?.let { return it in ids }
        val name = item.name.cleanName()
        return names.any { name.contains(it, ignoreCase = true) }
    }

    companion object {
        val SPIRIT_LEAP = SkyblockItem(setOf("SPIRIT_LEAP", "INFINITE_SPIRIT_LEAP"), listOf("Spirit Leap", "Infinileap"))
    }
}
