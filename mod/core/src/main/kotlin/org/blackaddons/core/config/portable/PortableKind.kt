package org.blackaddons.core.config.portable

import com.google.gson.JsonObject
import org.blackaddons.api.util.io.Portable
import org.blackaddons.api.util.io.bool
import org.blackaddons.core.actions.ActionPortable
import org.blackaddons.core.features.impl.visual.fme.FmePortable
import org.blackaddons.core.font.FontPortable
import org.blackaddons.core.theme.ThemePortable
import java.awt.Color

internal data class DiffValue(val text: String, val color: Color? = null)

internal class DiffRow(val label: String, val before: DiffValue, val after: DiffValue)

internal sealed interface ImportPreview<out T> {

    /**
     * [rows] compare against the current state. Documents with nothing to compare (a new theme) show [swatches].
     * [apply] returns null if saving failed.
     */
    class Ready<T>(
        val headline: String,
        val note: String,
        val rows: List<DiffRow> = emptyList(),
        val swatches: List<Color> = emptyList(),
        val canApply: Boolean = true,
        val apply: () -> T?,
    ) : ImportPreview<T>

    class Failed(val message: String) : ImportPreview<Nothing>
}

internal abstract class PortableKind<T>(val kind: String) {

    abstract val title: String

    /** Used in "that's a theme, not feature settings". */
    abstract val noun: String

    abstract fun preview(json: JsonObject): ImportPreview<T>

    /** Runs after an import from `/b import`, not from a screen. */
    open fun afterImport(value: T) {}

    /** [asProfile] marks a whole profile for [profilePreview]. */
    protected fun encode(document: JsonObject, asProfile: Boolean): String =
        Portable.encode(kind, if (asProfile) asProfile(document) else document)

    protected fun asProfile(document: JsonObject): JsonObject = document.apply { addProperty(PROFILE, true) }

    /** A whole profile imports as a new profile named [name]. Anything less joins [activeProfile]. */
    protected fun profilePreview(
        json: JsonObject,
        name: String,
        activeProfile: String,
        rows: List<DiffRow>,
        asNewProfile: () -> T?,
        addToActive: () -> T?,
    ): ImportPreview<T> {
        val tr = PortableImports.tr
        if (json.bool(PROFILE)) {
            return ImportPreview.Ready(
                headline = tr("new_profile", "New profile \"%s\"", name),
                note = tr("profile_note", "Imported as a profile of its own; yours stay as they are. %d items:", rows.size),
                rows = rows,
                apply = asNewProfile,
            )
        }
        return ImportPreview.Ready(
            headline = tr("into_profile", "Adds to your profile \"%s\"", activeProfile),
            note = tr("items", "%d items:", rows.size),
            rows = rows,
            apply = addToActive,
        )
    }

    private companion object {
        const val PROFILE = "profile"
    }
}

internal object PortableKinds {

    val all: List<PortableKind<*>> get() = listOf(ThemePortable, FeatureSettingsKind(), ActionPortable, FmePortable, ConfigPortable, FontPortable)

    fun byKind(kind: String): PortableKind<*>? = all.firstOrNull { it.kind == kind }
}
