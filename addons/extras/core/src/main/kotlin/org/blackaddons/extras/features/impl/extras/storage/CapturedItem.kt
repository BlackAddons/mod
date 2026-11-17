package org.blackaddons.extras.features.impl.extras.storage

import com.google.gson.JsonObject
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.str

/**
 * One captured stack. [raw] is the host format ([org.blackaddons.api.world.ItemHost]) we redraw it from, [name] lets
 * :mod:core search without re-parsing.
 */
internal data class CapturedItem(
    val raw: String,
    val name: String,
    val count: Int,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("raw", raw)
        addProperty("name", name)
        addProperty("count", count)
    }

    fun matches(query: String): Boolean = name.containsIgnoreCase(query)

    companion object {
        fun fromJson(o: JsonObject): CapturedItem? =
            o.str("raw")?.let { CapturedItem(it, o.str("name").orEmpty(), o.int("count", 1)) }
    }
}
