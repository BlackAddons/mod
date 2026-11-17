package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.settings.Setting

open class ModeSetting(
    name: String,
    default: String,
    val modes: List<String>,
    id: String = name,
) : Setting<String>(id, name, default), Savable {

    init {
        require(default in modes) { "Default '$default' not in modes $modes" }
    }

    /** Lossy: values not in [modes] are ignored. */
    override fun normalize(value: String): String = if (value in modes) value else this.value

    /** Picked from a list instead of cycled with arrows. Set it with `asDropdown()`. */
    var isDropdown: Boolean = false

    val index: Int get() = modes.indexOf(value)

    fun next() { value = modes[(index + 1).mod(modes.size)] }
    fun previous() { value = modes[(index - 1).mod(modes.size)] }

    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement?) {
        if (element?.isJsonPrimitive == true) {
            val read = element.asString
            if (read in modes) value = read
        }
    }
}
