package org.blackaddons.extras.features.impl.extras.storage

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.blackaddons.api.util.io.array
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.long
import org.blackaddons.api.util.io.str

/** One storage page as it was last opened. [items] is row-major, [rows] x [cols], null is an empty slot. */
internal data class CapturedContainer(
    val type: ContainerType,
    val page: Int,
    val title: String,
    val rows: Int,
    val cols: Int,
    val items: List<CapturedItem?>,
    val capturedAt: Long,
) {
    /** e.g. `enderchest:3`. */
    val key: String get() = StoragePatterns.containerKey(type, page)

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("type", type.name)
        addProperty("page", page)
        addProperty("title", title)
        addProperty("rows", rows)
        addProperty("cols", cols)
        addProperty("capturedAt", capturedAt)
        add("items", JsonArray().apply {
            items.forEach { add(it?.toJson() ?: JsonNull.INSTANCE) }
        })
    }

    companion object {
        fun fromJson(o: JsonObject): CapturedContainer = CapturedContainer(
            type = ContainerType.valueOf(o.str("type").orEmpty()),
            page = o.int("page"),
            title = o.str("title").orEmpty(),
            rows = o.int("rows"),
            cols = o.int("cols", StorageLayout.GRID_COLS),
            capturedAt = o.long("capturedAt"),
            items = o.array("items")?.map { (it as? JsonObject)?.let(CapturedItem::fromJson) }.orEmpty(),
        )
    }
}
