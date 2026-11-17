package org.blackaddons.core.gamedata

import com.google.gson.JsonObject
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.gamedata.SkyblockAreas.prettify
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.ServedDocument

/**
 * Activity id -> island name, served over a compiled table. Ids lie (`dynamic` is your island, `combat_1` is Spider's
 * Den, `instanced` is Kuudra), so [prettify] alone isn't enough.
 */
object SkyblockAreas {

    private const val KEY = "locations"

    fun load() = ServedDocument.load("${Backend.GAMEDATA_URL}/$KEY", "gamedata/$KEY") { document ->
        current = parse(document)
        Log.main.debug("Skyblock areas loaded from game data: {} islands", current.size)
    }

    internal val COMPILED: Map<String, String> = mapOf(
        "hub" to "Hub",
        "rift" to "The Rift",
        "garden" to "The Garden",
        "safari" to "Safari",
        "winter" to "Jerry's Workshop",
        "dungeon" to "Catacombs",
        "dynamic" to "Private Island",
        "galatea" to "Galatea",
        "combat_1" to "Spider's Den",
        "combat_2" to "Femboy Island (Blazing Fortress)",
        "combat_3" to "The End",
        "mining_1" to "Gold Mine",
        "mining_2" to "Deep Caverns",
        "mining_3" to "Dwarven Mines",
        "farming_1" to "The Barn",
        "farming_2" to "Mushroom Desert",
        "instanced" to "Kuudra",
        "mineshaft" to "Mineshaft",
        "foraging_1" to "The Park",
        "dungeon_hub" to "Dungeon Hub",
        "lotus_atoll" to "Lotus Atoll",
        "crimson_isle" to "Crimson Isle",
        "dark_auction" to "Dark Auction",
        "garden_guest" to "Garden Guest",
        "torrhus_canyon" to "Torrhus Canyon",
        "backwater_bayou" to "Backwater Bayou",
        "crystal_hollows" to "Crystal Hollows",
        "moonglade_marsh" to "Moonglade Marsh",
        "private_island_guest" to "Private Island Guest",
    )

    @Volatile
    internal var current: Map<String, String> = COMPILED

    val allNames: List<String>
        get() = current.values.distinct().sorted()

    fun name(slug: String): String = current[slug] ?: prettify(slug)

    /** Merged over the compiled table, so a stale document can't hide newer islands. */
    internal fun parse(document: JsonObject): Map<String, String> {
        val areas = document.get("areas") as? JsonObject ?: return COMPILED

        val served = LinkedHashMap<String, String>()
        for ((slug, value) in areas.entrySet()) {
            // String primitives only (numbers would turn into strings). Blank names get dropped.
            val name = value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.takeIf { it.isNotBlank() } ?: continue
            served[slug] = name
        }
        return if (served.isEmpty()) COMPILED else COMPILED + served
    }

    /** `crystal_hollows` -> `Crystal Hollows`. */
    internal fun prettify(slug: String): String = slug
        .split('_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
