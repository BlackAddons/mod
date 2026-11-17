package org.blackaddons.core.ui.screen.cosmetic.wardrobe

import org.blackaddons.api.cosmetics.Cosmetic3dItem
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.core.cosmetics.TintHex
import org.blackaddons.core.cosmetics.mesh.AdjustAxis
import org.blackaddons.core.cosmetics.mesh.Cosmetic3dEntry
import org.blackaddons.core.cosmetics.mesh.CosmeticModifiers
import org.blackaddons.core.cosmetics.mesh.ModifierAxis
import java.awt.Color

/**
 * Unsaved edits to one cosmetic and their resolve. Controls read the draft, not the stored entry. Nothing gets written
 * until commit. [onPreviewChanged] fires when the resolve changes, so the owner can publish previews.
 */
internal class WardrobeDraft(
    private val state: WardrobeState,
    private val onPreviewChanged: () -> Unit,
) {

    // The instance that seeded it. Reloads return new instances, so reseeding follows data changes, not the id.
    private var seededFrom: Cosmetic3dEntry? = null

    val tints = LinkedHashMap<String, String>()
    val modifiers = LinkedHashMap<String, String>()
    val adjust = LinkedHashMap<String, Double>()

    // Sliders per axis, rebuilt with the draft. A Slider owns its drag, so it has to live longer than the frame.
    private val adjustSliders = LinkedHashMap<String, Slider>()

    var resolved: Cosmetic3dItem? = null
        private set

    /** What the fitting room puts on u, kept apart from the mannequins it's merged with. */
    var selfPreview: List<Cosmetic3dItem>? = null
        private set

    private var stale = false

    fun sliderFor(axisId: String): Slider? = adjustSliders[axisId]

    /** True when [entry] isn't the instance already drafted, because a reload replaced it. */
    fun needsSeeding(entry: Cosmetic3dEntry): Boolean = entry !== seededFrom

    fun seed(entry: Cosmetic3dEntry) {
        seededFrom = entry
        tints.clear()
        tints.putAll(entry.tints)
        modifiers.clear()
        modifiers.putAll(entry.modifiers)
        seedAdjust(entry)
        refresh(entry)
    }

    fun clear() {
        seededFrom = null
        tints.clear()
        modifiers.clear()
        adjust.clear()
        adjustSliders.clear()
        resolved = null
        selfPreview = null
        stale = false
    }

    fun setModifier(axisId: String, optionId: String, entry: Cosmetic3dEntry) {
        modifiers[axisId] = optionId
        refresh(entry)
    }

    fun setTint(slot: String, hex: String) {
        tints[slot] = hex
        stale = true
    }

    /**
     * Rebuilds each frame when stale, because picker and sliders fire on each drag event and resolving copies the
     * definition.
     */
    fun applyPendingEdit(entry: Cosmetic3dEntry?) {
        if (!stale) return
        stale = false
        refresh(entry)
    }

    fun refresh(entry: Cosmetic3dEntry?) {
        if (entry == null) {
            resolved = null
            selfPreview = null
        } else {
            resolved = CosmeticModifiers.previewItem(entry, modifiers, tints, adjust)
            val others = state.equipped.filter { it.id != entry.id }.mapNotNull(state::resolveStored)
            selfPreview = others + listOfNotNull(resolved)
        }
        onPreviewChanged()
    }

    /** Sliders drive the draft directly, so the drag shows live on the model. */
    private fun seedAdjust(entry: Cosmetic3dEntry) {
        adjust.clear()
        adjustSliders.clear()
        for (axis in entry.adjustAxes) {
            val value = (entry.adjust[axis.id] ?: axis.neutral).coerceIn(axis.min, axis.max)
            adjust[axis.id] = value
            adjustSliders[axis.id] = Slider(axis.min, axis.max, value, step = adjustStep(axis)) { picked ->
                adjust[axis.id] = picked
                stale = true
            }.apply { defaultValue = axis.neutral.takeIf { axis.neutralInRange } }
        }
    }

    fun isDirty(entry: Cosmetic3dEntry?): Boolean = entry?.let {
        tints != it.tints || modifiers != it.modifiers || adjust != storedAdjust(it)
    } ?: false

    /** Placement as sliders see it. An untouched axis is absent on the server but drafted neutral, so it is not dirty. */
    private fun storedAdjust(entry: Cosmetic3dEntry): Map<String, Double> =
        entry.adjustAxes.associate { axis ->
            axis.id to (entry.adjust[axis.id] ?: axis.neutral).coerceIn(axis.min, axis.max)
        }

    fun chosenOption(axis: ModifierAxis): String =
        modifiers[axis.id]?.takeIf { id -> axis.options.any { it.id == id } } ?: axis.default

    /** Slots a layer really paints with. The definition can declare tints nothing reads. */
    fun tintSlots(): List<String> = resolved?.layers.orEmpty().mapNotNull { it.tintSlot }.distinct()

    fun tintColor(slot: String): Color {
        val argb = tints[slot]?.let(TintHex::parse) ?: resolved?.tints?.get(slot) ?: DEFAULT_TINT
        return Color(argb, true)
    }

    /** Only slots whose layer opted into alpha. Elsewhere the renderer forces opaque. */
    fun supportsAlpha(slot: String): Boolean =
        resolved?.layers.orEmpty().any { it.tintSlot == slot && it.supportsAlpha }

    private companion object {
        /** Shown when a slot has no color at all. The backend shouldn't send that, but it can. */
        const val DEFAULT_TINT = 0xFFFFFFFF.toInt()

        const val FINE_STEP = 0.005
        const val COARSE_STEP = 0.01
        const val COARSE_STEP_RANGE = 0.5

        /** Small ranges get the finer step (a 0.3 range at 0.01 is only thirty stops). */
        fun adjustStep(axis: AdjustAxis): Double =
            if (axis.max - axis.min <= COARSE_STEP_RANGE) FINE_STEP else COARSE_STEP
    }
}
