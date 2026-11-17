package org.blackaddons.core.features.impl.ui

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.hudChromaText
import org.blackaddons.api.hud.hudPanel
import java.awt.Color
import java.util.Locale

internal object PerformanceHud : Feature("Compact FPS / Ping / TPS overlay.") {

    private val textColor by color("Text Color", Color.WHITE, allowAlpha = true, textChroma = true)
        .withDescription("Color of the counter text.")
    private val bgColor by color("Background", Color(0, 0, 0, 110), allowAlpha = true)
        .withDescription("Background fill behind the counters.")
    private val showBorder by boolean("Border", true)
        .withDescription("Draw a 1px outline around the box.")
    private val borderColor by color("Border Color", Color(255, 255, 255, 60), allowAlpha = true)
        .withDescription("Color of the outline.")
        .showIf { showBorder.value }
    private val cornerRadius by number(
        name = "Corner Radius",
        default = 0.0, min = 0.0, max = 8.0, step = 0.5, unit = "px",
    ).withDescription("Round the panel corners. 0 keeps them square.")

    @Suppress("unused")
    private val hud = hudElement(
        "Performance HUD",
        defaultOffsetX = 5f, defaultOffsetY = 5f,
    ) { example ->
        hudPanel(
            background = bgColor.value,
            border = if (showBorder.value) borderColor.value else null,
            radius = cornerRadius.value.toFloat(),
        ) {
            for (line in lines(example)) hudChromaText(line, textColor)
        }
    }

    private fun lines(example: Boolean): List<String> {
        val fps = if (example) 144 else Platform.gameStats.fps
        val ping = if (example) 42 else Platform.gameStats.ping
        val tps = if (example) 20.0 else (Platform.gameStats.tps ?: 20.0)
        return listOf(
            "FPS: $fps",
            "Ping: ${ping}ms",
            "TPS: %.1f".format(Locale.ROOT, tps),
        )
    }
}
