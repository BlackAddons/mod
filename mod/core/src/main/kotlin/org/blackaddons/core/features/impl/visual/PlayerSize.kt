package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.cosmetics.Scale
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.core.cosmetics.mesh.MIN_SIDE_SCALE
import org.blackaddons.core.cosmetics.mesh.withoutSideMirror

/**
 * Local-only model scale per axis: 1 normal, 0 flat, negative height flips. Width and depth stop at zero
 * ([withoutSideMirror]). Overrides the [Cosmetics] size for yourself.
 */
internal object PlayerSize : Feature("Resize your own model. 1 is normal, 0 flattens an axis, a negative height turns you upside down.") {

    override val section = Categories.Visual.Self

    private val sizeX by number("Width (X)", 1.0, MIN_SIDE_SCALE.toDouble(), 2.0, 0.05, id = "Width")
        .withDescription("Horizontal scale across your body.")
    private val sizeY by number("Height (Y)", 1.0, -2.0, 2.0, 0.05, id = "Height")
        .withDescription("Vertical scale.")
    private val sizeZ by number("Depth (Z)", 1.0, MIN_SIDE_SCALE.toDouble(), 2.0, 0.05, id = "Depth")
        .withDescription("Front-to-back scale.")

    fun scale(): Scale? =
        if (enabled) Scale(sizeX.value.toFloat(), sizeY.value.toFloat(), sizeZ.value.toFloat()) else null
}
