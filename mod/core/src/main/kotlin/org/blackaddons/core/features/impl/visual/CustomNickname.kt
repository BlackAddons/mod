package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.cosmetics.ColorSpec
import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import java.awt.Color

/** Local-only custom name over your head and in tab (text plus solid or chroma). [Cosmetics] has gradients. */
internal object CustomNickname : Feature("Show yourself a custom name over your head and in the tab list.") {

    override val section = Categories.Visual.Self

    private val nameText by string("Name", "", placeholder = "Your name", maxLength = 32)
        .withDescription("The name to display for yourself. Empty keeps your real name.")
    private val color by color("Color", Color.WHITE, allowAlpha = false)
        .withDescription("Color of the custom name. Enable Chroma for a rotating rainbow.")

    fun local(): Cosmetic? {
        if (!enabled) return null
        val text = nameText.value.trim()
        if (text.isEmpty()) return null
        val spec = if (color.chroma) ColorSpec(chroma = true, speed = color.chromaSpeed * GRADIENT_SPEED_PER_CYCLE)
        else ColorSpec(solidColor = color.baseColor.rgb and 0xFFFFFF)
        return Cosmetic(display = text, name = spec)
    }

    private const val GRADIENT_SPEED_PER_CYCLE = 3f
}
