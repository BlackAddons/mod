package org.blackaddons.core.ui.tab

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.FeatureCategory
import org.blackaddons.api.feature.FeatureSection
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.*
import org.blackaddons.api.ui.widget.setting.SettingWidget
import org.blackaddons.api.util.I18n
import org.blackaddons.core.feature.FeatureManager

/**
 * A category's feature list: scroll, one [FeatureRow] per feature and collapsible section captions. Rows own their
 * state and hit tests. This class orders them and routes input. Captions only show when 2+ sections have features
 * ([sectionBlocks]), and start expanded. Search results pass no [category] and stay flat.
 */
internal class FeatureCategoryTabController(
    private val featuresProvider: () -> List<Feature>,
    val category: FeatureCategory? = null,
    private val showCategoryBadge: Boolean = false,
    private val emptyMessageKey: String = "blackaddons.category.empty",
    private val highlightQuery: () -> String? = { null },
) : TabController {

    constructor(category: FeatureCategory) : this({ FeatureManager.byCategory(category) }, category)

    private val rows = mutableMapOf<String, FeatureRow>()

    // Per section, for the life of this controller (rebuilt each time the ClickGUI opens).
    private val collapsed = mutableSetOf<String>()
    private val collapseAnims = mutableMapOf<String, Animation>()
    private val captionHits = mutableMapOf<String, Rect>()
    private val drawnRows = mutableListOf<FeatureRow>()

    // Positions read the eased offset, so clicks during a glide hit what's drawn.
    private val scroll = Scroll().apply {
        trackBottomInset = Theme.RADIUS_LARGE
    }
    private val scrollOffset: Float get() = scroll.offset

    private val rowGap = 4f
    private val rowInset = Theme.PADDING_LARGE

    private var lastX = 0f
    private var lastY = 0f
    private var lastW = 0f
    private var lastH = 0f

    private fun features() = featuresProvider()
    private fun rowFor(f: Feature): FeatureRow = rows.getOrPut(f.id) { FeatureRow(f) }
    private fun rowWidth() = lastW - rowInset * 2f

    private fun blocks(): List<Block> = sectionBlocks(features(), category)

    private fun progress(block: Block): Float {
        val key = block.section?.id ?: return 1f
        // Search opens all sections. Collapsed matches would hide results.
        if (!highlightQuery().isNullOrBlank()) return 1f
        val anim = collapseAnims[key] ?: return if (key in collapsed) 0f else 1f
        return anim.get()
    }

    /** Shows only [section]. The category's other sections start collapsed, so it's in view without scrolling. */
    fun focus(section: FeatureSection) {
        collapseAnims.clear()
        collapsed.clear()
        category?.sections?.filter { it != section }?.forEach { collapsed += it.id }
    }

    private fun toggle(section: FeatureSection) {
        val nowCollapsed = section.id !in collapsed
        if (nowCollapsed) collapsed += section.id else collapsed -= section.id
        collapseAnims.getOrPut(section.id) {
            Animation(if (nowCollapsed) 1f else 0f, Theme.ANIM_NORMAL.toLong(), Easing.easeOutCubic)
        }.animateTo(if (nowCollapsed) 0f else 1f)
    }

    private fun captionSpace(block: Block, isFirst: Boolean): Float = when {
        block.section == null -> 0f
        isFirst -> captionHeight + rowGap
        else -> CAPTION_GAP_ABOVE + captionHeight + rowGap
    }

    private val captionHeight: Float get() = Render.textHeight(Theme.FONT_SMALL, Fonts.medium) + 6f

    private fun bodyHeight(block: Block, rw: Float, query: String?): Float =
        block.features.sumOf { (rowFor(it).height(rw, query) + rowGap).toDouble() }.toFloat()

    override fun render(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        stashViewport(x, y, width, height)
        drawnRows.clear()
        if (features().isEmpty()) {
            Render.text(
                I18n.tr(emptyMessageKey),
                x + rowInset, y + Theme.PADDING_LARGE,
                TextStyle.BODY_MUTED,
            )
            return
        }

        val query = highlightQuery()
        val rw = width - rowInset * 2f
        val list = blocks()

        var totalContent = 0f
        list.forEachIndexed { i, block ->
            totalContent += captionSpace(block, i == 0) + bodyHeight(block, rw, query) * progress(block)
        }
        scroll.update(x, y, width, height, totalContent)

        Render.pushScissor(x, y, width, height)

        // Things that aren't drawn lose their hitbox.
        captionHits.values.forEach(Rect::clear)

        var rowY = y - scrollOffset
        list.forEachIndexed { i, block ->
            val space = captionSpace(block, i == 0)
            val section = block.section
            if (section != null) {
                val captionY = rowY + space - captionHeight - rowGap
                drawCaption(section, x + rowInset, captionY, rw, progress(block))
                captionHits.getOrPut(section.id) { Rect() }.set(x + rowInset, captionY, rw, captionHeight)
            }
            rowY += space

            val shown = bodyHeight(block, rw, query) * progress(block)
            if (progress(block).isVisible) {
                Render.pushScissor(x, rowY, width, shown)
                var bodyY = rowY
                for (feature in block.features) {
                    val row = rowFor(feature)
                    val rh = row.height(rw, query)
                    if (bodyY + rh >= y && bodyY <= y + height) {
                        row.render(x + rowInset, bodyY, rw, mouseX, mouseY, query, showCategoryBadge)
                        drawnRows += row
                    }
                    bodyY += rh + rowGap
                }
                Render.popScissor()
            }
            rowY += shown
        }

        Render.popScissor()

        drawnRows.forEach { it.renderOverlays(mouseX, mouseY) }
        scroll.draw()
    }

    private fun overlaying(): SettingWidget? = drawnRows.firstNotNullOfOrNull { it.overlaying() }

    private fun drawCaption(section: FeatureSection, x: Float, y: Float, width: Float, progress: Float) {
        val cy = y + captionHeight / 2f
        Chevron.draw(x + 3f, cy, 3.5f, 1.3f, progress)

        val label = section.translatedLabel
        val labelX = x + CAPTION_LABEL_INDENT
        Render.text(
            label, labelX,
            Render.centerY(y, captionHeight, TextStyle.SMALL_MUTED_MEDIUM),
            TextStyle.SMALL_MUTED_MEDIUM,
        )

        val lineX = labelX + Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium) + 8f
        val lineEnd = x + width
        if (lineEnd > lineX) Render.rect(lineX, cy, lineEnd - lineX, 1f, Theme.BORDER)
    }

    /** Visible rows top-down with live positions, for input. Skips half-open sections (rows that slide under a clip). */
    private inline fun walkRows(block: (row: FeatureRow, rx: Float, ry: Float, rw: Float, query: String?) -> Boolean): Boolean {
        val query = highlightQuery()
        val rw = rowWidth()
        var rowY = lastY - scrollOffset
        blocks().forEachIndexed { i, group ->
            rowY += captionSpace(group, i == 0)
            val shown = progress(group)
            if (shown < 0.999f) {
                rowY += bodyHeight(group, rw, query) * shown
                return@forEachIndexed
            }
            for (feature in group.features) {
                val row = rowFor(feature)
                if (block(row, lastX + rowInset, rowY, rw, query)) return true
                rowY += row.height(rw, query) + rowGap
            }
        }
        return false
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (overlaying()?.mouseClicked(mouse) == true) return true
        if (scroll.mouseClicked(mouse)) return true
        if (mouse.x !in lastX..(lastX + lastW) || mouse.y !in lastY..(lastY + lastH)) return false

        if (mouse.isLeft) {
            val hit = captionHits.entries.firstOrNull { it.value.has(mouse.x, mouse.y) }
            if (hit != null) {
                category?.sections?.firstOrNull { it.id == hit.key }?.let(::toggle)
                return true
            }
        }
        return walkRows { row, rx, ry, rw, query -> row.mouseClicked(mouse, rx, ry, rw, query) }
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        var consumed = scroll.mouseReleased(mouse)
        for (feature in features()) if (rowFor(feature).mouseReleased(mouse)) consumed = true
        return consumed
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        if (scroll.mouseDragged(drag)) return true
        for (feature in features()) if (rowFor(feature).mouseDragged(drag)) return true
        return false
    }

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (overlaying()?.mouseScrolled(wheel) == true) return true
        // Rows route scroll by their widgets' hitboxes.
        val handled = walkRows { row, _, _, _, query -> row.mouseScrolled(wheel, query) }
        return handled || scroll.mouseScrolled(wheel)
    }

    override fun keyPressed(key: KeyInput): Boolean {
        for (feature in features()) if (rowFor(feature).keyPressed(key)) return true
        return false
    }

    override fun charTyped(typed: CharInput): Boolean {
        for (feature in features()) if (rowFor(feature).charTyped(typed)) return true
        return false
    }

    private fun stashViewport(x: Float, y: Float, width: Float, height: Float) {
        lastX = x; lastY = y; lastW = width; lastH = height
    }

    private companion object {
        const val CAPTION_GAP_ABOVE = 8f
        const val CAPTION_LABEL_INDENT = 12f
    }
}

/** A section and its rows. A single null-section block is a flat list. */
internal class Block(val section: FeatureSection?, val features: List<Feature>)

/**
 * Splits [features] by [category]'s section order. Fewer than two non-empty sections become one block without a
 * caption, so sections can be declared before they're needed.
 */
internal fun sectionBlocks(features: List<Feature>, category: FeatureCategory?): List<Block> {
    val sections = category?.sections.orEmpty()
    if (sections.isEmpty() || features.isEmpty()) return listOf(Block(null, features))

    val bySection = features.groupBy { it.effectiveSection }
    val captioned = sections.mapNotNull { section ->
        bySection[section]?.let { Block(section, it) }
    }
    return if (captioned.size < 2) listOf(Block(null, features)) else captioned
}
