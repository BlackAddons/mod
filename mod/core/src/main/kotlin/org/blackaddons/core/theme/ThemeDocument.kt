package org.blackaddons.core.theme

import com.google.gson.JsonObject
import org.blackaddons.api.ui.theme.ThemeBases
import org.blackaddons.api.ui.theme.ThemeSlot
import org.blackaddons.api.ui.theme.ThemeSpec
import org.blackaddons.api.util.color.toColorOrNull
import org.blackaddons.api.util.color.toHex
import org.blackaddons.api.util.io.child
import org.blackaddons.api.util.io.str
import java.awt.Color

internal data class ThemeDocument(
    val id: String,
    val name: String,
    val spec: ThemeSpec,
    val builtIn: Boolean = false,
)

/**
 * Theme file format, also used inside portable strings:
 * ```json
 * { "id": "midnight", "name": "Midnight",
 *   "bases": { "surface": "#1C1E26FF", "text": "#E6EDF8FF", "accent": "#B46DF2FF" },
 *   "overrides": { "danger": "#F2334DFF" } }
 * ```
 */
internal object ThemeJson {

    fun write(document: ThemeDocument): JsonObject = JsonObject().apply {
        addProperty("id", document.id)
        addProperty("name", document.name)
        add("bases", JsonObject().apply {
            addProperty("surface", document.spec.bases.surface.toHex())
            addProperty("text", document.spec.bases.text.toHex())
            addProperty("accent", document.spec.bases.accent.toHex())
        })
        if (document.spec.overrides.isNotEmpty()) {
            add("overrides", JsonObject().apply {
                document.spec.overrides.forEach { (slot, color) -> addProperty(slot.id, color.toHex()) }
            })
        }
    }

    /** Null without bases. Unknown override slots get dropped, so newer themes still load. */
    fun read(json: JsonObject, fallbackId: String): ThemeDocument? {
        val bases = json.child("bases") ?: return null
        val surface = bases.color("surface") ?: return null
        val text = bases.color("text") ?: return null
        val accent = bases.color("accent") ?: return null

        val overrides = json.child("overrides")?.entrySet().orEmpty().mapNotNull { (key, value) ->
            val slot = ThemeSlot.byId(key) ?: return@mapNotNull null
            val color = value.takeIf { it.isJsonPrimitive }?.asString?.toColorOrNull() ?: return@mapNotNull null
            slot to color
        }.toMap()

        val id = json.str("id")?.takeIf { it.isNotBlank() } ?: fallbackId
        return ThemeDocument(
            id = id,
            name = json.str("name")?.takeIf { it.isNotBlank() } ?: id,
            spec = ThemeSpec(ThemeBases(surface, text, accent), overrides),
        )
    }

    private fun JsonObject.color(key: String): Color? = str(key)?.toColorOrNull()
}
