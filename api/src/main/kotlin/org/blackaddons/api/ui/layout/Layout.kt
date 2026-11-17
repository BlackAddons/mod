package org.blackaddons.api.ui.layout

import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import java.awt.Color

/**
 * Measured layout tree: every node knows its size, containers place children end-to-end. Rebuild each
 * frame and [draw] once.
 * ```
 * column(spacing = 6) {
 *     text(name, 15f, Theme.TEXT_PRIMARY, Fonts.bold)
 *     row(width = w) { text("Purse", ...); flexSpacer(); text(value, ...) }  // space-between
 * }.draw(x, y)
 * ```
 */
interface LayoutNode {
    val width: Float
    val height: Float

    /** Absolute scaled GUI px. */
    fun draw(x: Float, y: Float)
}

enum class Align {
    START, CENTER, END;

    internal fun offset(container: Float, child: Float): Float = when (this) {
        START -> 0f
        CENTER -> (container - child) / 2f
        END -> container - child
    }
}

class Spacer(override val width: Float = 0f, override val height: Float = 0f) :
    LayoutNode {
    override fun draw(x: Float, y: Float) {}
}

/** Takes spare main-axis space, split by [weight]. Needs a container with a fixed main size (e.g. `row(width = w)`). */
class Flex(val weight: Float = 1f) : LayoutNode {
    override val width = 0f
    override val height = 0f
    override fun draw(x: Float, y: Float) {}
}

/** Fixed-size leaf drawn by [render] at its top-left. */
class Cell(
    override val width: Float,
    override val height: Float,
    private val render: (x: Float, y: Float) -> Unit,
) : LayoutNode {
    override fun draw(x: Float, y: Float) = render(x, y)
}

/** Width is [fixedWidth] or widest child; height is [fixedHeight] or the sum. A fixed height feeds [Flex] children. */
class Column internal constructor(
    private val children: List<LayoutNode>,
    private val spacing: Float,
    private val align: Align,
    fixedWidth: Float?,
    fixedHeight: Float?,
) : LayoutNode {
    private val gaps = spacing * (children.size - 1).coerceAtLeast(0)
    private val rigidHeight = children.filter { it !is Flex }.sumOf { it.height.toDouble() }.toFloat()
    private val flexWeight = children.filterIsInstance<Flex>().sumOf { it.weight.toDouble() }.toFloat()

    override val width = fixedWidth ?: (children.maxOfOrNull { it.width } ?: 0f)
    override val height = fixedHeight ?: (rigidHeight + gaps)

    override fun draw(x: Float, y: Float) {
        val leftover = (height - rigidHeight - gaps).coerceAtLeast(0f)
        var cy = y
        children.forEachIndexed { i, child ->
            if (i > 0) cy += spacing
            if (child is Flex) {
                cy += if (flexWeight > 0f) leftover * (child.weight / flexWeight) else 0f
            } else {
                child.draw(x + align.offset(width, child.width), cy)
                cy += child.height
            }
        }
    }
}

/** Height is [fixedHeight] or tallest child; width is [fixedWidth] or the sum. A fixed width feeds [Flex] children. */
class Row internal constructor(
    private val children: List<LayoutNode>,
    private val spacing: Float,
    private val align: Align,
    fixedWidth: Float?,
    fixedHeight: Float?,
) : LayoutNode {
    private val gaps = spacing * (children.size - 1).coerceAtLeast(0)
    private val rigidWidth = children.filter { it !is Flex }.sumOf { it.width.toDouble() }.toFloat()
    private val flexWeight = children.filterIsInstance<Flex>().sumOf { it.weight.toDouble() }.toFloat()

    override val width = fixedWidth ?: (rigidWidth + gaps)
    override val height = fixedHeight ?: (children.maxOfOrNull { it.height } ?: 0f)

    override fun draw(x: Float, y: Float) {
        val leftover = (width - rigidWidth - gaps).coerceAtLeast(0f)
        var cx = x
        children.forEachIndexed { i, child ->
            if (i > 0) cx += spacing
            if (child is Flex) {
                cx += if (flexWeight > 0f) leftover * (child.weight / flexWeight) else 0f
            } else {
                child.draw(cx, y + align.offset(height, child.height))
                cx += child.width
            }
        }
    }
}

private class Padded(
    private val child: LayoutNode,
    private val left: Float, private val top: Float, private val right: Float, private val bottom: Float,
) : LayoutNode {
    override val width = child.width + left + right
    override val height = child.height + top + bottom
    override fun draw(x: Float, y: Float) = child.draw(x + left, y + top)
}

private class Backed(
    private val child: LayoutNode,
    private val color: Color,
    private val radius: Float,
) : LayoutNode {
    override val width = child.width
    override val height = child.height
    override fun draw(x: Float, y: Float) {
        Render.rect(x, y, width, height, color, radius)
        child.draw(x, y)
    }
}

private class Bordered(
    private val child: LayoutNode,
    private val color: Color,
    private val radius: Float,
) : LayoutNode {
    override val width = child.width
    override val height = child.height
    override fun draw(x: Float, y: Float) {
        child.draw(x, y)
        if (radius > 0f) {
            // One rounded stroke: AA hides the half-pixel stroke and corners don't overlap.
            Render.hollowRect(x, y, width, height, 1f, color, radius)
        } else {
            // 1px strips, so translucent corners aren't blended twice.
            Render.rect(x, y, width, 1f, color)
            Render.rect(x, y + height - 1f, width, 1f, color)
            Render.rect(x, y + 1f, 1f, height - 2f, color)
            Render.rect(x + width - 1f, y + 1f, 1f, height - 2f, color)
        }
    }
}

private class Faded(private val child: LayoutNode, private val alpha: Float) : LayoutNode {
    override val width = child.width
    override val height = child.height
    override fun draw(x: Float, y: Float) {
        if (alpha >= 1f) {
            child.draw(x, y)
            return
        }
        Render.push()
        Render.globalAlpha(alpha)
        try {
            child.draw(x, y)
        } finally {
            Render.pop()
        }
    }
}

fun LayoutNode.padded(all: Float): LayoutNode = padded(all, all)

fun LayoutNode.padded(horizontal: Float, vertical: Float): LayoutNode =
    Padded(this, horizontal, vertical, horizontal, vertical)

/** Pair with [padded] for a card. */
fun LayoutNode.background(color: Color, radius: Float = 0f): LayoutNode =
    Backed(this, color, radius)

/** Drawn inside the bounds. */
fun LayoutNode.bordered(color: Color, radius: Float = 0f): LayoutNode =
    Bordered(this, color, radius)

/** A node rather than a call, because HUD trees are built before they're drawn - a bare `globalAlpha` would leak. */
fun LayoutNode.faded(alpha: Float): LayoutNode = Faded(this, alpha)

class LayoutBuilder internal constructor() {
    private val nodes = mutableListOf<LayoutNode>()
    internal fun build(): List<LayoutNode> = nodes.toList()

    /** Any node: a sub-tree, a [org.blackaddons.api.ui.widget.Widget], a [Cell], ... */
    fun add(node: LayoutNode) { nodes += node }

    /** `column { +doneButton }`. */
    operator fun LayoutNode.unaryPlus() = add(this)

    fun spacer(width: Float = 0f, height: Float = 0f) = add(
        Spacer(
            width,
            height
        )
    )
    fun flexSpacer(weight: Float = 1f) = add(Flex(weight))

    /** Bridge to raw `Render`/widget draw calls. */
    fun cell(width: Float, height: Float, render: (x: Float, y: Float) -> Unit) = add(
        Cell(
            width,
            height,
            render
        )
    )

    fun text(text: String, size: Float, color: Color, font: Font) = add(
        Cell(
            Render.textWidth(text, size, font),
            Render.textHeight(size, font)
        ) { x, y ->
            Render.text(text, x, y, size, color, font)
        },
    )

    fun text(text: String, style: TextStyle) = add(
        Cell(Render.textWidth(text, style), Render.textHeight(style.size, style.font)) { x, y ->
            Render.text(text, x, y, style)
        },
    )

    fun column(spacing: Float = 0f, align: Align = Align.START, width: Float? = null, height: Float? = null, block: LayoutBuilder.() -> Unit) =
        add(makeColumn(spacing, align, width, height, block))

    fun row(spacing: Float = 0f, align: Align = Align.START, width: Float? = null, height: Float? = null, block: LayoutBuilder.() -> Unit) =
        add(makeRow(spacing, align, width, height, block))
}

private fun makeColumn(spacing: Float, align: Align, width: Float?, height: Float?, block: LayoutBuilder.() -> Unit): LayoutNode =
    Column(
        LayoutBuilder().apply(block).build(),
        spacing,
        align,
        width,
        height
    )

private fun makeRow(spacing: Float, align: Align, width: Float?, height: Float?, block: LayoutBuilder.() -> Unit): LayoutNode =
    Row(
        LayoutBuilder().apply(block).build(),
        spacing,
        align,
        width,
        height
    )

fun column(spacing: Float = 0f, align: Align = Align.START, width: Float? = null, height: Float? = null, block: LayoutBuilder.() -> Unit): LayoutNode =
    makeColumn(spacing, align, width, height, block)

fun row(spacing: Float = 0f, align: Align = Align.START, width: Float? = null, height: Float? = null, block: LayoutBuilder.() -> Unit): LayoutNode =
    makeRow(spacing, align, width, height, block)
