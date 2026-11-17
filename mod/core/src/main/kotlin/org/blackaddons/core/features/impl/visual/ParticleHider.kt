package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.Platform
import org.blackaddons.api.event.render.ParticleRenderEvent
import org.blackaddons.api.event.render.ParticleSpawnEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.core.ui.screen.ParticlePickerScreen

object ParticleHider : Feature(
    description = "Hides or limits world particles to improve visibility and performance.",
    name = "Particle Hider",
) {
    private val hideNearby by boolean(
        name = "Hide Nearby",
        default = true,
    ).withDescription("Hide particles within a close radius from the player.")

    private val nearbyRadius by number(
        name = "Nearby Radius",
        default = 3.0,
        min = 0.5,
        max = 16.0,
        step = 0.5,
        unit = "m",
    ).withDescription("Distance from the player within which particles are hidden.")
        .showIf { hideNearby.value }

    private val hideFar by boolean(
        name = "Hide Far",
        default = false,
    ).withDescription("Hide particles beyond a maximum distance from the player.")

    private val maxDistance by number(
        name = "Max Distance",
        default = 24.0,
        min = 2.0,
        max = 64.0,
        step = 2.0,
        unit = "m",
    ).withDescription("Distance beyond which particles are hidden.")
        .showIf { hideFar.value }

    private val filterByParticleType by boolean(
        name = "Filter by Type",
        default = false,
    ).withDescription("Hide specific particle types selected in the picker.")

    private val hiddenParticlesSetting by list(
        name = "Hidden Particle Types",
        default = emptyList(),
        placeholder = "e.g. minecraft:crit",
    ).hidden()

    @Suppress("unused")
    private val chooseParticles by button(
        name = "Choose Particles",
        action = { UiManager.push(ParticlePickerScreen(hiddenParticlesSetting, title = hiddenParticlesSetting.translatedName)) },
        label = "Select",
        id = "choose_particles",
    ).withDescription("Select which particle types to hide.")
        .showIf { filterByParticleType.value }

    override fun init() {
        register<ParticleSpawnEvent> {
            if (!Platform.player.isInGame) return@register

            if (filterByParticleType.value) {
                val hiddenList = hiddenParticlesSetting.value
                if (hiddenList.isNotEmpty() && event.typeId in hiddenList) {
                    event.cancelled = true
                    return@register
                }
            }

            if (hiddenByDistance(event.x, event.y, event.z)) event.cancelled = true
        }

        register<ParticleRenderEvent> {
            if (!Platform.player.isInGame) return@register
            if (hiddenByDistance(event.x, event.y, event.z)) event.cancelled = true
        }
    }

    private fun hiddenByDistance(x: Double, y: Double, z: Double): Boolean {
        val nearby = hideNearby.value
        val far = hideFar.value
        if (!nearby && !far) return false
        val dx = x - Platform.player.x
        val dy = y - Platform.player.y
        val dz = z - Platform.player.z
        val distSq = dx * dx + dy * dy + dz * dz
        return (nearby && distSq <= nearbyRadius.value * nearbyRadius.value) ||
            (far && distSq > maxDistance.value * maxDistance.value)
    }
}
