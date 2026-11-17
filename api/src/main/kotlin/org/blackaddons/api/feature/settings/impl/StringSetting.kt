package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.settings.Setting

/**
 * Single-line string; commits on Enter/blur, reverts on Esc. [accepts] only gates the widget (field turns
 * red); values already on disk are read as-is.
 */
open class StringSetting(
    name: String,
    default: String,
    val placeholder: String = "",
    val maxLength: Int = 64,
    id: String = name,
    open val isMasked: Boolean = false,
    val accepts: (String) -> Boolean = { true },
) : Setting<String>(id, name, default), Savable {

    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement?) {
        val p = element?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return
        if (p.isString) value = p.asString.take(maxLength)
    }
}
