package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.event.render.VisionObstruction
import org.blackaddons.api.event.render.VisionObstructionEvent
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.core.features.impl.ui.ScreenQol

/** Cheat-edition [ScreenQol]: clear vision through fluids, blinding effects and blocks. */
object ScreenQolCheats : FeatureExtension(ScreenQol, group = "Cheats") {
    private val clearWaterVision by boolean("Clear Water Vision", false)
        .withDescription("Removes the fog and the screen overlay while your head is underwater.")
    private val clearLavaVision by boolean("Clear Lava Vision", false)
        .withDescription("Removes the thick fog while your head is in lava.")
    private val clearEffectVision by boolean("Clear Effect Vision", false)
        .withDescription("Removes the fog from Blindness and Darkness, and the Darkness pulse.")
    private val clearBlockVision by boolean("Clear Block Vision", false)
        .withDescription("Removes the block texture covering your screen while your head is inside a block.")

    override fun init() {
        register<VisionObstructionEvent> {
            event.cancelled = when (event.obstruction) {
                VisionObstruction.WATER -> clearWaterVision.value
                VisionObstruction.LAVA -> clearLavaVision.value
                VisionObstruction.BLINDNESS, VisionObstruction.DARKNESS -> clearEffectVision.value
                VisionObstruction.IN_BLOCK -> clearBlockVision.value
            }
        }
    }
}
