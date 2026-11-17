package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.lerpColor
import java.awt.Color
import java.awt.geom.Path2D
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Donut chart whose segments own their hover targets and tooltips. */
class DonutChart : Widget {

    /** Non-positive or non-finite values are ignored. */
    data class Segment(val label: String, val value: Double, val color: Color, val tooltip: String = label)

    internal data class Slice(val segmentIndex: Int, val segment: Segment, val startDegrees: Float, val sweepDegrees: Float)

    internal class Outline(val segmentIndex: Int, val segment: Segment, val points: FloatArray) {
        // Same outline as drawing, curved joins included.
        val path: Path2D.Float by lazy {
            Path2D.Float().apply {
                moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                closePath()
            }
        }
    }

    var segments: List<Segment> = emptyList()
    var progress: Float = 1f
    /** Null follows the theme. */
    var track: Color? = null
    var center: Color? = null

    /** From a linked legend; pointer hover wins. */
    var highlightedIndex: Int? = null

    var hoveredIndex: Int? = null
        private set

    val activeIndex: Int?
        get() = hoveredIndex ?: highlightedIndex?.takeIf { it in segments.indices }

    var boxWidth: Float = 0f
    var boxHeight: Float = 0f
    override val width: Float get() = boxWidth
    override val height: Float get() = boxHeight

    private var animatedSegments: List<Segment> = emptyList()
    private val emphasis = mutableListOf<Animation>()

    override fun draw(x: Float, y: Float) {
        val radius = min(width, height) / 2f
        if (!radius.isFinite() || radius <= 0f) {
            hoveredIndex = null
            return
        }
        val centerX = x + width / 2f
        val centerY = y + height / 2f
        val outlines = outlines(radius)
        val mouseX = (UiMouse.x - centerX).toDouble()
        val mouseY = (UiMouse.y - centerY).toDouble()
        hoveredIndex = outlines.firstOrNull { it.path.contains(mouseX, mouseY) }?.segmentIndex
        syncEmphasis()
        val emphasisValues = emphasis.map(Animation::get)
        val focus = emphasisValues.maxOrNull() ?: 0f

        Render.push()
        try {
            Render.translate(centerX, centerY)
            Render.circle(0f, 0f, radius, track ?: Theme.TRACK)
            outlines.forEach { outline ->
                val own = emphasisValues.getOrNull(outline.segmentIndex) ?: 0f
                val dim = (focus - own).coerceIn(0f, 1f)
                val muted = lerpColor(outline.segment.color, outline.segment.color.withAlpha(DIMMED_ALPHA), dim)
                Render.polygon(outline.points, lerpColor(muted, Color.WHITE, own * FOCUS_BRIGHTEN))
            }
            Render.circle(0f, 0f, (radius - THICKNESS).coerceAtLeast(0f), center ?: Theme.SURFACE)
        } finally {
            Render.pop()
        }

        hoveredIndex?.let(segments::getOrNull)?.tooltip?.let(Tooltip::show)
    }

    private fun syncEmphasis() {
        if (segments != animatedSegments) {
            animatedSegments = segments.toList()
            emphasis.clear()
            repeat(segments.size) { emphasis += Animation(durationMs = Theme.ANIM_HOVER.toLong()) }
        }
        val active = activeIndex
        emphasis.forEachIndexed { index, animation -> animation.animateTo(if (index == active) 1f else 0f) }
    }

    internal fun visibleSlices(segments: List<Segment>, progress: Float): List<Slice> {
        var remainingDegrees = FULL_TURN * progress.coerceIn(0f, 1f)
        return slices(segments).mapNotNull { slice ->
            val visibleSweep = min(slice.sweepDegrees, remainingDegrees)
            remainingDegrees -= visibleSweep
            slice.takeIf { visibleSweep > 0f }?.copy(sweepDegrees = visibleSweep)
        }
    }

    internal fun outlines(radius: Float): List<Outline> {
        if (!radius.isFinite() || radius <= 0f) return emptyList()
        val slices = visibleSlices(segments, progress)
        val innerRadius = (radius - THICKNESS).coerceAtLeast(0f)
        val middle = (radius + innerRadius) / 2f
        val halfThickness = (radius - innerRadius) / 2f
        val closed = progress >= 1f
        // Small slices bend less so they stay visible.
        fun bend(left: Slice, right: Slice): Float = min(
            Math.toDegrees((halfThickness / middle).toDouble()).toFloat(),
            min(left.sweepDegrees, right.sweepDegrees) / 2f,
        )

        return slices.mapIndexed { index, slice ->
            val startBend = when {
                slices.size == 1 -> 0f
                index > 0 -> bend(slices[index - 1], slice)
                closed -> bend(slices.last(), slice)
                else -> 0f
            }
            val endBend = when {
                slices.size == 1 -> 0f
                index < slices.lastIndex -> bend(slice, slices[index + 1])
                closed -> bend(slice, slices.first())
                else -> 0f
            }
            val steps = ceil(slice.sweepDegrees / DEGREES_PER_STEP).toInt().coerceAtLeast(2)
            val points = FloatArray((2 * (steps + 1) + 2 * (JOIN_STEPS - 1)) * 2)
            var offset = 0
            fun point(r: Float, degrees: Float) {
                val angle = Math.toRadians((degrees - QUARTER_TURN).mod(FULL_TURN).toDouble())
                points[offset++] = cos(angle).toFloat() * r
                points[offset++] = sin(angle).toFloat() * r
            }
            val start = slice.startDegrees
            val end = start + slice.sweepDegrees
            for (step in 0..steps) point(radius, start + slice.sweepDegrees * step / steps)
            for (step in 1 until JOIN_STEPS) {
                val t = Math.PI * step / JOIN_STEPS
                point(middle + halfThickness * cos(t).toFloat(), end + endBend * sin(t).toFloat())
            }
            for (step in steps downTo 0) point(innerRadius, start + slice.sweepDegrees * step / steps)
            for (step in JOIN_STEPS - 1 downTo 1) {
                val t = Math.PI * step / JOIN_STEPS
                point(middle + halfThickness * cos(t).toFloat(), start + startBend * sin(t).toFloat())
            }
            Outline(slice.segmentIndex, slice.segment, points)
        }
    }

    private fun slices(segments: List<Segment>): List<Slice> {
        val visible = segments.withIndex().filter { it.value.value.isFinite() && it.value.value > 0.0 }
        val total = visible.sumOf { it.value.value }
        if (!total.isFinite() || total <= 0.0) return emptyList()

        var accumulated = 0.0
        return visible.map { indexed ->
            val segment = indexed.value
            val start = accumulated / total * FULL_TURN
            accumulated += segment.value
            val end = accumulated / total * FULL_TURN
            Slice(indexed.index, segment, start.toFloat(), (end - start).toFloat())
        }
    }

    companion object {

        /** Golden-angle steps keep neighbors distinct. */
        fun colors(count: Int): List<Color> {
            require(count >= 0) { "count must not be negative" }
            if (count == 0) return emptyList()
            return List(count) { index -> Color.getHSBColor((HUE_START + index * HUE_STEP) % 1f, SATURATION, BRIGHTNESS) }
        }

        private const val FULL_TURN = 360f
        private const val QUARTER_TURN = 90f
        private const val DEGREES_PER_STEP = 4f
        private const val JOIN_STEPS = 12
        private const val THICKNESS = 10f
        private const val HUE_START = 0.77f
        private const val HUE_STEP = 0.381966f
        private const val SATURATION = 0.58f
        private const val BRIGHTNESS = 0.92f
        private const val DIMMED_ALPHA = 105
        private const val FOCUS_BRIGHTEN = 0.18f
    }
}
