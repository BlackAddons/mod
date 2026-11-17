package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import java.awt.Color

object ProgressBar {

    fun draw(
        x: Float, y: Float, width: Float, height: Float, progress: Double,
        fill: Color = Theme.ACCENT, track: Color = Theme.TRACK,
    ) {
        val radius = height / 2f
        Render.rect(x, y, width, height, track, radius)
        val p = progress.coerceIn(0.0, 1.0).toFloat()
        if (p <= 0f) return
        // At least as wide as tall so low progress still looks rounded.
        val fillW = (width * p).coerceAtLeast(height)
        Render.rect(x, y, fillW, height, fill, radius)
    }

    fun drawIndeterminate(
        x: Float, y: Float, width: Float, height: Float,
        fill: Color = Theme.ACCENT, track: Color = Theme.TRACK,
    ) {
        val radius = height / 2f
        Render.rect(x, y, width, height, track, radius)
        if (width <= 0f) return
        val segmentWidth = (width * 0.28f).coerceAtLeast(height * 2f)
        val phase = (System.currentTimeMillis() % INDETERMINATE_PERIOD_MS).toFloat() / INDETERMINATE_PERIOD_MS
        val segmentX = x - segmentWidth + (width + segmentWidth) * phase
        Render.pushScissor(x, y, width, height)
        Render.rect(segmentX, y, segmentWidth, height, fill, radius)
        Render.popScissor()
    }

    private const val INDETERMINATE_PERIOD_MS = 1_100L
}
