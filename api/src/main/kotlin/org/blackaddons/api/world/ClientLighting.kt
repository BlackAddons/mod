package org.blackaddons.api.world

import org.blackaddons.api.Platform

/** Disables the client light engine: full light everywhere. A plain slot as mixins read it per query. Render-only. */
object ClientLighting {
    @Volatile
    @JvmStatic
    var disabled = false
        set(value) {
            if (field == value) return
            field = value
            Platform.world.redrawWorld()
        }
}
