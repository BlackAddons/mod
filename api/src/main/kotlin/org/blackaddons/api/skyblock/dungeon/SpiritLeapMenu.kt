package org.blackaddons.api.skyblock.dungeon

import org.blackaddons.api.Platform
import org.blackaddons.api.util.stripFormatting
import java.util.TreeMap

/** Hypixel's teammate picker, opened by Spirit Leap and Infinileap. */
object SpiritLeapMenu {

    private val TITLES = setOf("Spirit Leap", "Teleport to Player")
    private val HEAD_NAME = Regex("""^(?:\[[^]]+] )?(\w{1,16})""")
    private val LEAPED = Regex("""^You have teleported to (\w{1,16})!$""")
    private const val PLAYER_HEAD = "minecraft:player_head"

    fun isMenu(title: String?): Boolean = title in TITLES

    val isOpen: Boolean get() = isMenu(Platform.container.title())

    fun headOwner(itemName: String): String? = HEAD_NAME.find(itemName.stripFormatting().trim())?.groupValues?.get(1)

    /** Name -> container slot, looked up ignoring case. Heads load after the menu opens, so this starts empty. */
    fun heads(): Map<String, Int> {
        val heads = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
        val raw = Platform.container.capture()?.raw ?: return heads
        raw.forEachIndexed { slot, item ->
            val info = item?.let(Platform.items::describe)?.takeIf { it.id == PLAYER_HEAD } ?: return@forEachIndexed
            headOwner(info.name)?.let { heads.putIfAbsent(it, slot) }
        }
        return heads
    }

    fun headSlot(name: String): Int? = heads()[name]

    /** The teammate a leap landed on, from the unformatted chat line; null for any other line. */
    fun leapedTo(message: String): String? = LEAPED.find(message)?.groupValues?.get(1)
}
