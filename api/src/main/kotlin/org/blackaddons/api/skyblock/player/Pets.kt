package org.blackaddons.api.skyblock.player

import com.google.gson.JsonObject
import org.blackaddons.api.HostApi
import org.blackaddons.api.util.cleanName
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.api.world.ItemInfo

/** [uuid] is known once the pet has been seen as an item (in the pets menu). Chat lines only give its name. */
data class Pet(val name: String, val level: Int? = null, val uuid: String? = null) {

    /** True for the same pet: compared by [uuid] when both have one, otherwise by name. */
    fun sameAs(other: Pet): Boolean =
        if (uuid != null && other.uuid != null) uuid == other.uuid else matches(other.name)

    fun matches(name: String): Boolean = normalize(this.name).contains(normalize(name))

    companion object {
        private val ITEM_NAME = Regex("""^(?:\S+\s+)?\[Lvl\s+(\d+)]\s+(.+?)(?:\s+✦)?$""")

        /** Reads a pet item (from the pets menu or the inventory). Null for anything else. */
        fun of(item: ItemInfo): Pet? {
            val info = item.petInfo?.let(::parseJsonObject) ?: return null
            val display = ITEM_NAME.matchEntire(item.name.stripFormatting().trim())
            val name = display?.groupValues?.get(2)?.trim()
                ?: info.string("type")?.split('_')?.joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }
                ?: return null
            return Pet(name, display?.groupValues?.get(1)?.toIntOrNull(), item.skyblockUuid ?: info.string("uuid"))
        }

        fun normalize(name: String): String = name.cleanName().replace("✦", "").trim()

        private fun JsonObject.string(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }
}

enum class PetChangeCause { SUMMON, DESPAWN, AUTOPET, MENU }

/**
 * The summoned pet, from chat and the pets menu, remembered per profile across restarts. Read it on the client thread.
 */
object Pets {

    @Volatile
    private var bound: PetProvider? = null

    private val provider: PetProvider
        get() = bound ?: error("Pets was read before the host bound its provider")

    /** Host only. */
    @HostApi
    fun bind(provider: PetProvider) {
        synchronized(this) {
            bound?.let { error("Pets is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    /** Null when no pet is out or the pet is not known yet. */
    val current: Pet? get() = provider.current

    fun isSummoned(name: String): Boolean = current?.matches(name) == true
}

/** Host side of [Pets], bound once. */
interface PetProvider {
    val current: Pet?
}
