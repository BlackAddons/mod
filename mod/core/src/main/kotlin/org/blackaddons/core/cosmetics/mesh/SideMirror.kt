package org.blackaddons.core.cosmetics.mesh
import org.blackaddons.api.cosmetics.Scale
import kotlin.math.abs

/**
 * Only height can be negative, because mirrored width or depth inverts faces. Old stored negatives keep their size (-1
 * -> 1).
 */
internal fun Scale.withoutSideMirror(): Scale = if (x >= 0f && z >= 0f) this else Scale(abs(x), y, abs(z))

internal const val MIN_SIDE_SCALE = 0f
