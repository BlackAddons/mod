package org.blackaddons.core.cosmetics

import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.Scale
import org.blackaddons.core.features.impl.visual.PlayerSize
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The size part of [PlayerCosmetics]: local [PlayerSize], server cosmetic and the editor preview. Local wins for
 * yourself. Read through `Platform.cosmetics.scaleFor`. [setPreview] isn't on the facade, so addons can't force a
 * scale.
 */
object PlayerScale {

    /** Editor preview override. Refreshed every frame and expires soon after, so closing the editor reverts it. */
    @Volatile
    private var preview: Scale? = null
    @Volatile
    private var previewAt = TimeSource.Monotonic.markNow()

    fun setPreview(scale: Scale?) {
        preview = scale
        previewAt = TimeSource.Monotonic.markNow()
    }

    fun of(uuid: UUID?): Scale? {
        if (uuid == null) return null
        if (uuid == Platform.player.uuid) {
            preview?.takeIf { previewAt.elapsedNow() < PREVIEW_TTL }?.let { return it }
            PlayerSize.scale()?.let { return it }
        }
        return PlayerCosmetics.server(uuid)?.size
    }

    private val PREVIEW_TTL = 200.milliseconds
}
