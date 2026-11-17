package org.blackaddons.platform.ui.render

import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import org.blackaddons.api.util.concurrent.LoadOnce
import org.blackaddons.api.util.io.str
import java.util.zip.GZIPInputStream

/**
 * The vanilla model each Skyblock item drew with before Hypixel's resource pack, keyed by Skyblock id. Without the
 * pack, a newer item sits on plain paper, and this puts back the sword, bow or head it used to be. The table comes from
 * PackDisabler (CC0, see `licenses/packdisabler/NOTICE.txt`).
 */
internal object LegacyItemModels {

    private const val RESOURCE = "/assets/blackaddons/skyblock/legacy_item_models.json.gz"

    /** [texture] is the skin value of a head, otherwise null. */
    class Look(val model: Identifier, val texture: String?)

    // 2.5 MB of JSON, so it parses on a worker instead of stalling the first frame that shows an item.
    private val table = LoadOnce<Unit, Map<String, Look>>("legacy-item-models") { load() }

    /** Null until the table has loaded. A caller must not cache a fallback made while it's null. */
    fun ready(): Map<String, Look>? = table[Unit]

    private fun load(): Map<String, Look>? {
        val stream = LegacyItemModels::class.java.getResourceAsStream(RESOURCE) ?: return null
        val root = GZIPInputStream(stream).reader().use(JsonParser::parseReader).asJsonObject
        return buildMap(root.size()) {
            for ((id, value) in root.entrySet()) {
                val entry = value.asJsonObject
                val model = entry.str("model")?.let(Identifier::tryParse) ?: continue
                put(id, Look(model, entry.str("texture")))
            }
        }
    }
}
