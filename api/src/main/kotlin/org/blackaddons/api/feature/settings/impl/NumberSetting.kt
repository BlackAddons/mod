package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.util.snapToStep

open class NumberSetting(
    name: String,
    default: Double,
    val min: Double,
    val max: Double,
    val step: Double = 1.0,
    val unit: String = "",
    id: String = name,
) : Setting<Double>(id, name, default.snapToStep(min, max, step)), Savable {

    /** Lossy: clamped to `[min, max]` and snapped to [step] (5.7 may read back as 6.0). */
    override fun normalize(value: Double): Double = value.snapToStep(min, max, step)

    override fun write(): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement?) {
        if (element?.isJsonPrimitive == true) value = element.asDouble
    }
}
