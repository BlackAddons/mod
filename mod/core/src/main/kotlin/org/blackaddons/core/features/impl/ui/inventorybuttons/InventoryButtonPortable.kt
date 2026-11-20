package org.blackaddons.core.features.impl.ui.inventorybuttons

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.util.io.array
import org.blackaddons.api.util.io.bool
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.str
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.config.portable.DiffRow
import org.blackaddons.core.config.portable.DiffValue
import org.blackaddons.core.config.portable.ImportPreview
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.config.portable.PortableKind

internal object InventoryButtonPortable : PortableKind<List<InventoryButton>>("inventory-buttons") {
    private val tr = PortableImports.tr.scope("inventory_buttons")
    private val gson = Gson()

    override val title: String get() = tr("title", "Import inventory buttons")
    override val noun: String get() = tr("noun", "inventory buttons")

    fun export(buttons: List<InventoryButton>): String =
        encode(JsonObject().apply { add(BUTTONS, gson.toJsonTree(buttons)) }, asProfile = false)

    fun exportProfile(profile: ButtonProfile): String = encode(
        JsonObject().apply {
            addProperty(NAME, profile.name)
            add(BUTTONS, gson.toJsonTree(profile.buttons))
        },
        asProfile = true,
    )

    override fun preview(json: JsonObject): ImportPreview<List<InventoryButton>> {
        val buttons = read(json)
        if (buttons.isEmpty()) return ImportPreview.Failed(tr("err_empty", "That string has no buttons in it."))
        val name = json.str(NAME)?.trim()?.take(MAX_TEXT)?.ifEmpty { null } ?: tr("imported_name", "Imported")
        return profilePreview(
            json, name, InventoryButtonStore.active.name, buttons.mapIndexed(::row),
            asNewProfile = {
                InventoryButtonProfiles.create(name, buttons)
                buttons
            },
            addToActive = { add(buttons) },
        )
    }

    private fun add(buttons: List<InventoryButton>): List<InventoryButton>? {
        InventoryButtonEditor.remember()
        InventoryButtonStore.buttons += buttons
        return buttons.takeIf { InventoryButtonStore.save() }
    }

    private fun row(index: Int, button: InventoryButton): DiffRow {
        val runs = when (button.runs) {
            ButtonRuns.COMMAND -> button.command.takeIf(String::isNotBlank)?.let { "/$it" } ?: EMPTY
            ButtonRuns.SHORTCUT -> if (ActionStorage.shortcuts.any { it.id.toString() == button.shortcutId }) {
                tr("row_shortcut", "Shortcut")
            } else {
                tr("row_shortcut_missing", "Shortcut this game doesn't have")
            }
        }
        return DiffRow(button.label.ifBlank { tr("row_unnamed", "Button %d", index + 1) }, DiffValue(EMPTY), DiffValue(runs))
    }

    // Field by field, since a pasted string is untrusted and Gson would put nulls into non-null fields.
    internal fun read(json: JsonObject): List<InventoryButton> =
        (json.array(BUTTONS) ?: JsonArray()).asSequence().mapNotNull { it as? JsonObject }.take(MAX_BUTTONS).map(::readButton).toList()

    private fun readButton(o: JsonObject) = InventoryButton(
        x = o.int("x").coerceIn(-MAX_OFFSET, MAX_OFFSET),
        y = o.int("y").coerceIn(-MAX_OFFSET, MAX_OFFSET),
        anchorRight = o.bool("anchorRight"),
        anchorBottom = o.bool("anchorBottom"),
        width = o.int("width", InventoryButton.DEFAULT_SIZE).coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE),
        height = o.int("height", InventoryButton.DEFAULT_SIZE).coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE),
        icon = o.text("icon", MAX_ICON),
        label = o.text("label", MAX_TEXT),
        runs = ButtonRuns.entries.firstOrNull { it.name == o.str("runs") } ?: ButtonRuns.COMMAND,
        command = o.text("command", MAX_TEXT).removePrefix("/"),
        shortcutId = o.text("shortcutId", MAX_TEXT),
        scope = ButtonScope.entries.firstOrNull { it.name == o.str("scope") } ?: ButtonScope.INVENTORY,
        titleFilter = o.text("titleFilter", MAX_TEXT),
        insideGui = o.bool("insideGui"),
    )

    private fun JsonObject.text(key: String, max: Int): String = str(key).orEmpty().take(max)

    private const val BUTTONS = "buttons"
    private const val NAME = "name"
    private const val EMPTY = "-"
    private const val MAX_BUTTONS = 200
    private const val MAX_OFFSET = 2000
    private const val MAX_TEXT = 256
    private const val MAX_ICON = 4096
}
