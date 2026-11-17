package org.blackaddons.core.gamedata

import com.google.gson.JsonObject
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.ServedDocument

/**
 * Absolute skill caps, served over a compiled minimum (Foraging moved 50 -> 54 -> 57). `RawHypixelParser` computes the
 * Farming and Taming caps per player.
 */
object SkillCaps {

    private const val KEY = "skill_caps"
    private const val DEFAULT_CEILING = 50

    fun load() = ServedDocument.load("${Backend.GAMEDATA_URL}/$KEY", "gamedata/$KEY") { document ->
        current = parse(document)
        Log.main.debug("Skill caps loaded from game data: {} skills", current.size)
    }

    /** From Hypixel 0.27.0.18 (2026-08). */
    internal val COMPILED: Map<String, Int> = mapOf(
        "farming" to 60, "mining" to 60, "combat" to 60, "foraging" to 57,
        "fishing" to 50, "enchanting" to 60, "alchemy" to 50, "taming" to 60,
        "carpentry" to 50, "runecrafting" to 25, "social" to 25, "hunting" to 50,
    )

    @Volatile
    internal var current: Map<String, Int> = COMPILED

    /** Non-blocking, so safe on the render thread. */
    internal fun ceiling(lowerKey: String): Int = current[lowerKey] ?: DEFAULT_CEILING

    /** Merged over the compiled table, so a stale document can't lower a known cap. */
    internal fun parse(document: JsonObject): Map<String, Int> {
        val skills = document.get("skills") as? JsonObject ?: return COMPILED

        val served = LinkedHashMap<String, Int>()
        for ((key, value) in skills.entrySet()) {
            val cap = value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: continue
            if (cap > 0) served[key.lowercase()] = cap
        }
        return if (served.isEmpty()) COMPILED else COMPILED + served
    }
}
