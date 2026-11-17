package org.blackaddons.core.features.impl.dev

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.io.Gsons
import java.awt.Color

/** Dev tool: copies the hovered item's serialized data (e.g. a head's base64 skin, Skyblock NBT). */
internal object CopyItemData : Feature(
    description = "Copy the hovered item's data to the clipboard.",
    name = "Copy Item Data",
) {
    private val key by keybind("Copy Key")
        .withDescription("Press while hovering an item in a container to copy its data.")

    internal fun pretty(raw: String): String =
        runCatching { Gsons.pretty.toJson(JsonParser.parseString(raw)) }.getOrDefault(raw)

    override fun init() {
        onKeybind(key, allowInScreens = true) {
            val raw = Platform.container.hoveredItemRaw() ?: return@onKeybind
            Platform.clipboard.copy(pretty(raw))
            Platform.chat.local(
                I18n.trOr("blackaddons.copy_item_data.copied", "Copied item data to clipboard."),
                Color(0x55FF55),
            )
        }
    }
}
