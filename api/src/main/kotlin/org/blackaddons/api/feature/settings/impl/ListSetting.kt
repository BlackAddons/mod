package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.ui.widget.moveItem

/** String list edited in a modal. Each change stores a fresh list - don't mutate the returned one. */
open class ListSetting(
    name: String,
    default: List<String> = emptyList(),
    val placeholder: String = "",
    val itemMaxLength: Int = 64,
    id: String = name,
) : Setting<List<String>>(id, name, default), Savable {

    /** Choices every entry carries, picked on its row in the editor. Set via `withEntryOptions`. */
    var entryOptions: List<EntryOption> = emptyList()
        internal set

    /** Per entry, the choices that differ from their default. Replaced on each change, never mutated. */
    var entryChoices: Map<String, Map<String, String>> = emptyMap()
        private set

    override fun normalize(value: List<String>): List<String> = value.toList()

    fun choiceOf(entry: String, option: EntryOption): String =
        entryChoices[entry]?.get(option.id)?.takeIf { it in option.choices } ?: option.default

    fun setChoice(entry: String, option: EntryOption, choice: String) {
        if (entry !in value || choice !in option.choices) return
        val rest = entryChoices[entry].orEmpty() - option.id
        val next = if (choice == option.default) rest else rest + (option.id to choice)
        entryChoices = if (next.isEmpty()) entryChoices - entry else entryChoices + (entry to next)
    }

    fun add(entry: String) {
        val trimmed = entry.trim().take(itemMaxLength)
        if (trimmed.isEmpty() || trimmed in value) return
        value = value + trimmed
    }

    fun removeAt(index: Int) {
        if (index !in value.indices) return
        entryChoices = entryChoices - value[index]
        value = value.toMutableList().also { it.removeAt(index) }
    }

    override fun reset() {
        entryChoices = emptyMap()
        super.reset()
    }

    /** Out-of-range indices are ignored. */
    fun move(from: Int, to: Int) {
        value = value.toMutableList().also { it.moveItem(from, to) }
    }

    /** An entry with non-default choices is an object (`value` plus option ids); every other one a string. */
    override fun write(): JsonElement = JsonArray().apply {
        for (entry in value) {
            val choices = entryChoices[entry]
            if (choices.isNullOrEmpty()) {
                add(JsonPrimitive(entry))
                continue
            }
            add(JsonObject().apply {
                addProperty(ENTRY_VALUE, entry)
                choices.forEach { (option, choice) -> addProperty(option, choice) }
            })
        }
    }

    override fun read(element: JsonElement?) {
        if (element?.isJsonArray != true) return
        val read = element.asJsonArray.mapNotNull { e ->
            when {
                e.isJsonPrimitive && e.asJsonPrimitive.isString -> e.asString.take(itemMaxLength) to emptyMap()
                e.isJsonObject -> {
                    val obj = e.asJsonObject
                    val entry = obj.get(ENTRY_VALUE)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?: return@mapNotNull null
                    entry.asString.take(itemMaxLength) to entryOptions.mapNotNull { option ->
                        val choice = obj.get(option.id)?.takeIf { it.isJsonPrimitive }?.asString
                        choice?.takeIf { it != option.default && it in option.choices }?.let { option.id to it }
                    }.toMap()
                }
                else -> null
            }
        }.distinctBy { it.first }
        entryChoices = read.filter { it.second.isNotEmpty() }.toMap()
        value = read.map { it.first }
    }

    private companion object {
        const val ENTRY_VALUE = "value"
    }
}

/**
 * A choice each entry of a [ListSetting] carries; [choices] first is the default. Two choices toggle, more open a
 * menu. With a [label] (shown as is, e.g. `Aa`) two choices are an on/off chip, lit on the second.
 */
class EntryOption(val id: String, val choices: List<String>, val label: String? = null) {
    init {
        require(choices.size >= 2) { "Entry option $id needs at least two choices" }
    }

    val default: String get() = choices.first()
}
