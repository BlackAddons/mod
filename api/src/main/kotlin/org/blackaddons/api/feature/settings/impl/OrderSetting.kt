package org.blackaddons.api.feature.settings.impl

/**
 * All of [options], in an order the player drags into place. Unknown entries are dropped and missing ones are added
 * at the end.
 */
open class OrderSetting(
    name: String,
    val options: List<String>,
    id: String = name,
) : ListSetting(name, options, id = id) {

    override fun normalize(value: List<String>): List<String> {
        val kept = value.filter { it in options }.distinct()
        return kept + options.filter { it !in kept }
    }
}
