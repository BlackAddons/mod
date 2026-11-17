package org.blackaddons.core.gamedata

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.ServedDocument

internal class Leveling(
    val skill: LongArray,
    val catacombs: LongArray,
    val slayer: LongArray,
    val vampire: LongArray,
    val pet: LongArray,
)

object LevelingTables {

    private const val KEY = "leveling"
    private const val PATH = "/assets/blackaddons/gamedata/$KEY.json"

    internal val BUNDLED: Leveling = bundled()

    @Volatile
    internal var current: Leveling = BUNDLED
        private set

    fun load() = ServedDocument.load("${Backend.GAMEDATA_URL}/$KEY", "gamedata/$KEY") { document ->
        current = parse(document)
        Log.main.debug("Leveling tables loaded from game data")
    }

    internal fun parse(document: JsonObject, floor: Leveling = BUNDLED) = Leveling(
        skill = cumulative(document, "skill", floor.skill),
        catacombs = cumulative(document, "catacombs", floor.catacombs),
        slayer = cumulative(document, "slayer", floor.slayer),
        vampire = cumulative(document, "vampire", floor.vampire),
        pet = table(document, "pet", floor.pet)?.takeIf { costs -> costs.all { it > 0 } } ?: floor.pet,
    )

    private fun cumulative(document: JsonObject, key: String, floor: LongArray): LongArray {
        val table = table(document, key, floor) ?: return floor
        val valid = table.isNotEmpty() && table[0] == 0L && (1 until table.size).all { table[it] >= table[it - 1] }
        return if (valid) table else floor
    }

    private fun table(document: JsonObject, key: String, floor: LongArray): LongArray? {
        val array = document.get(key) as? JsonArray ?: return null
        if (array.size() < floor.size) return null
        if (array.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isNumber }) return null
        return LongArray(array.size()) { array[it].asLong }
    }

    private fun bundled(): Leveling {
        val document = LevelingTables::class.java.getResourceAsStream(PATH)
            ?.use { JsonParser.parseString(it.readBytes().decodeToString()).asJsonObject }
            ?: error("$PATH missing from the jar")
        val empty = LongArray(0)
        val tables = parse(document, Leveling(empty, empty, empty, empty, empty))
        check(listOf(tables.skill, tables.catacombs, tables.slayer, tables.vampire, tables.pet).all { it.isNotEmpty() }) {
            "$PATH is incomplete or invalid"
        }
        return tables
    }
}
