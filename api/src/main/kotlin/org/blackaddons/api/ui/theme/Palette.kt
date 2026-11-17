package org.blackaddons.api.ui.theme

import java.awt.Color

enum class ThemeBase(val id: String, val label: String) {
    SURFACE("surface", "Surface"),
    TEXT("text", "Text"),
    ACCENT("accent", "Accent"),
}

/** Groups slots in an editor. */
enum class ThemeSlotGroup(val id: String, val label: String) {
    SURFACES("surfaces", "Surfaces"),
    TEXT("text", "Text"),
    ACCENTS("accents", "Accents"),
}

/**
 * One UI color slot. Closed set, so screens can't depend on colors a theme lacks. [id] is stored and stable.
 * [derivedFrom] names the base the formula reads, or null if fixed.
 */
enum class ThemeSlot(
    val id: String,
    val group: ThemeSlotGroup,
    val label: String,
    val derivedFrom: ThemeBase?,
) {
    BACKGROUND("background", ThemeSlotGroup.SURFACES, "Background", ThemeBase.SURFACE),
    SURFACE("surface", ThemeSlotGroup.SURFACES, "Surface", ThemeBase.SURFACE),
    SURFACE_HOVER("surfaceHover", ThemeSlotGroup.SURFACES, "Surface, hovered", ThemeBase.SURFACE),
    SURFACE_ACTIVE("surfaceActive", ThemeSlotGroup.SURFACES, "Surface, active", ThemeBase.SURFACE),
    BORDER("border", ThemeSlotGroup.SURFACES, "Border", ThemeBase.SURFACE),
    OVERLAY_DIM("overlayDim", ThemeSlotGroup.SURFACES, "Overlay dim", null),
    TRACK("track", ThemeSlotGroup.SURFACES, "Track", null),
    ACCENT("accent", ThemeSlotGroup.ACCENTS, "Accent", ThemeBase.ACCENT),
    ACCENT_SOFT("accentSoft", ThemeSlotGroup.ACCENTS, "Accent, soft", ThemeBase.ACCENT),
    SUCCESS("success", ThemeSlotGroup.ACCENTS, "Success", null),
    DANGER("danger", ThemeSlotGroup.ACCENTS, "Danger", null),
    TEXT_PRIMARY("textPrimary", ThemeSlotGroup.TEXT, "Primary text", ThemeBase.TEXT),
    TEXT_SECONDARY("textSecondary", ThemeSlotGroup.TEXT, "Secondary text", ThemeBase.TEXT),
    TEXT_MUTED("textMuted", ThemeSlotGroup.TEXT, "Muted text", ThemeBase.TEXT),
    TEXT_DISABLED("textDisabled", ThemeSlotGroup.TEXT, "Disabled text", ThemeBase.TEXT),
    ;

    companion object {
        private val index = entries.associateBy(ThemeSlot::id)

        /** Null when a stored theme names a dropped slot. */
        fun byId(id: String): ThemeSlot? = index[id]
    }
}

/** Immutable; build with [ThemeSpec.resolve]. */
class Palette(color: (ThemeSlot) -> Color) {

    private val colors = Array(ThemeSlot.entries.size) { color(ThemeSlot.entries[it]) }

    operator fun get(slot: ThemeSlot): Color = colors[slot.ordinal]

    override fun equals(other: Any?): Boolean = other is Palette && colors.contentEquals(other.colors)

    override fun hashCode(): Int = colors.contentHashCode()
}
