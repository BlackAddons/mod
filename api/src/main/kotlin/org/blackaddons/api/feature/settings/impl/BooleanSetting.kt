package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.settings.Setting

open class BooleanSetting(
    name: String,
    default: Boolean = false,
    id: String = name,
) : Setting<Boolean>(id, name, default), Savable {

    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement?) {
        if (element?.isJsonPrimitive == true) value = element.asBoolean
    }
}
