package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.render.LightmapRenderEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.world.ClientLighting

object Fullbright : Feature(
    description = "Keeps the world fully lit without changing your gamma setting.",
    name = "Fullbright",
) {
    private val disableLightEngine by boolean("Disable Light Engine", default = true)
        .withDescription("Turns the client light engine off while fullbright is on, for more FPS.")

    override fun init() {
        register<LightmapRenderEvent> {
            event.fullbright = true
        }
        register<ClientTickEvent.Post> {
            ClientLighting.disabled = disableLightEngine.value
        }
    }

    override fun onDisable() {
        super.onDisable()
        ClientLighting.disabled = false
    }
}
