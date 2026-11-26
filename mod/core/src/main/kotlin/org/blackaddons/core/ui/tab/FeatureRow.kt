package org.blackaddons.core.ui.tab

import org.blackaddons.api.feature.*
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.*
import org.blackaddons.api.ui.widget.setting.SettingWidget
import org.blackaddons.api.util.I18n
import org.blackaddons.core.feature.FeatureLabels
import org.blackaddons.core.feature.FeatureManager
import java.awt.Color
import kotlin.math.cos

/**
 * One feature row: header (name, badge, toggle) and, when open, description and settings. Only owns interactive state.
 * Layout and search are in [FeatureSettings]. A search [query] opens and highlights it.
 */
internal class FeatureRow(private val feature: Feature) {

    private val settings = FeatureSettings(feature)

    private var expanded = false
    private val hoverAnim = Animation(0f, Theme.ANIM_HOVER.toLong())
    private val toggleAnim = Animation(if (feature.enabled) 1f else 0f, Theme.ANIM_FAST.toLong(), Easing.easeOutCubic)
    private val expandAnim = Animation(0f, Theme.ANIM_NORMAL.toLong(), Easing.easeOutCubic)
    private val groupExpanded = mutableSetOf<String>()
    private val groupAnims = mutableMapOf<String, Animation>()

    // What the last draw placed. Input reads these instead of replaying layout, which drifted during animation.
    private val groupHeaders = mutableMapOf<String, Rect>()
    private val drawnWidgets = mutableListOf<SettingWidget>()

    private val unsafeAnims = mutableMapOf<String, Animation>()

    /**
     * Wrap cache keyed by text and width. Every row is measured each frame, and a short search opens dozens. Without
     * it, the renderer's layout cache overflows. Keying on text also covers language switches.
     */
    private var wrapKey: Pair<String, Float>? = null
    private var wrappedDescription: List<String> = emptyList()

    private fun isOpen(query: String?): Boolean = expanded || settings.forceOpen(query)
    private fun groupProgress(group: String, query: String?): Float {
        if (settings.groupForceOpen(group, query)) return 1f
        val anim = groupAnims[group] ?: return if (group in groupExpanded) 1f else 0f
        return anim.get()
    }

    private fun toggleGroup(group: String) {
        val nowExpanded = group !in groupExpanded
        if (nowExpanded) groupExpanded.add(group) else groupExpanded.remove(group)
        val anim = groupAnims.getOrPut(group) {
            Animation(if (nowExpanded) 0f else 1f, Theme.ANIM_NORMAL.toLong(), Easing.easeOutCubic)
        }
        anim.animateTo(if (nowExpanded) 1f else 0f)
    }

    private fun expansionProgress(query: String?): Float {
        if (settings.forceOpen(query)) return 1f
        return expandAnim.get()
    }

    private fun contentWidth(rw: Float): Float = rw - LABEL_INDENT - Theme.PADDING

    private fun stackHeight(widgets: List<SettingWidget>): Float =
        widgets.sumOf { it.height.toDouble() }.toFloat() + widgets.size * ROW_GAP

    /** Wrapped description, used for both measuring and drawing, so the clip matches. */
    private fun descriptionLines(rw: Float): List<String> {
        val text = feature.translatedDescription ?: I18n.tr("blackaddons.feature.no_description")
        val width = contentWidth(rw)
        val key = text to width
        if (wrapKey != key) {
            wrappedDescription = Render.wrapText(text, width, Theme.FONT_SMALL, Fonts.regular)
            wrapKey = key
        }
        return wrappedDescription
    }

    private fun descriptionHeight(rw: Float): Float =
        descriptionLines(rw).size * Render.textHeight(Theme.FONT_SMALL, Fonts.regular) + 8f

    private fun expandedBlockHeight(rw: Float, query: String?): Float {
        // Same helpers as render(), so the clip matches.
        var h = descriptionHeight(rw) + stackHeight(settings.ungrouped())
        for ((group, ws) in settings.groups()) {
            h += GROUP_HEADER_HEIGHT + ROW_GAP
            h += stackHeight(ws) * groupProgress(group, query)
        }
        return h + SETTINGS_BLOCK_PAD * 2f
    }

    fun height(rw: Float, query: String?): Float {
        // Collapsed rows skip measuring (wrapping and widget text).
        val t = expansionProgress(query)
        return if (t <= 0f) COLLAPSED_HEIGHT else COLLAPSED_HEIGHT + expandedBlockHeight(rw, query) * t
    }

    fun render(x: Float, y: Float, rw: Float, mouseX: Float, mouseY: Float, query: String?, showCategoryBadge: Boolean) {
        val rh = height(rw, query)
        val hovered = hit(mouseX, mouseY, x, y, rw, COLLAPSED_HEIGHT)
        hoverAnim.animateTo(if (hovered) 1f else 0f)
        val hoverIntensity = hoverAnim.get()

        val expandT = expansionProgress(query)
        val bgAlpha = (60f + 60f * expandT).toInt().coerceIn(0, 255)
        Render.rect(x, y, rw, rh, Theme.SURFACE.withAlpha(bgAlpha), Theme.RADIUS)
        if (hoverIntensity.isVisible) {
            Render.rect(x, y, rw, COLLAPSED_HEIGHT, Theme.SURFACE_HOVER.withAlpha(hoverIntensity * 0.35f), Theme.RADIUS)
        }

        drawChevron(x, y, expandT)

        val baseColor = if (feature.isRunning) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY
        val name = feature.translatedName
        renderHighlighted(
            name,
            query,
            x + LABEL_INDENT,
            Render.centerY(y, COLLAPSED_HEIGHT, Theme.FONT_BODY, Fonts.medium),
            Theme.FONT_BODY,
            baseColor,
            Fonts.medium,
        )
        val nameRight = x + LABEL_INDENT + Render.textWidth(name, Theme.FONT_BODY, Fonts.medium)
        drawLabels(FeatureLabels.onRow(feature), nameRight + 6f, y, COLLAPSED_HEIGHT, mouseX, mouseY)

        if (showCategoryBadge) {
            /*
             * Search results span tabs, so the badge names the tab, plus a declared section. Derived sections aren't
             * shown, because their caption can be hidden.
             */
            val section = feature.section
            val badge = feature.category.translatedLabel.let {
                if (section == null) it else "$it / ${section.translatedLabel}"
            }
            val badgeW = Render.textWidth(badge, Theme.FONT_SMALL, Fonts.regular)
            val badgeX = x + rw - Theme.PADDING - TOGGLE_WIDTH - badgeW - 10f
            Render.text(
                badge,
                badgeX,
                Render.centerY(y, COLLAPSED_HEIGHT, TextStyle.SMALL_MUTED),
                TextStyle.SMALL_MUTED,
            )
        }

        val tx = x + rw - Theme.PADDING - TOGGLE_WIDTH
        val ty = centered(y, COLLAPSED_HEIGHT, TOGGLE_HEIGHT)
        renderFeatureToggle(tx, ty)

        // Things that aren't drawn lose their hitbox.
        groupHeaders.values.forEach(Rect::clear)
        drawnWidgets.clear()

        if (expandT.isVisible) {
            val visibleH = expandedBlockHeight(rw, query) * expandT
            Render.pushScissor(x, y + COLLAPSED_HEIGHT, rw, visibleH)

            var by = y + COLLAPSED_HEIGHT + SETTINGS_BLOCK_PAD
            val lineH = Render.textHeight(Theme.FONT_SMALL, Fonts.regular)
            for (line in descriptionLines(rw)) {
                Render.text(line, x + LABEL_INDENT, by, TextStyle.SMALL_MUTED)
                by += lineH
            }
            by += 8f

            val contentW = contentWidth(rw)
            for (widget in settings.ungrouped()) {
                renderSettingRow(widget, x + LABEL_INDENT, by, contentW, mouseX, mouseY, query)
                by += widget.height + ROW_GAP
            }

            for ((group, ws) in settings.groups()) {
                val gp = groupProgress(group, query)
                val groupLabels = extensionsIn(ws).flatMap(FeatureLabels::of)
                drawGroupHeader(translatedSettingGroup(group), x + LABEL_INDENT, by, rw - LABEL_INDENT - Theme.PADDING, gp, groupLabels, mouseX, mouseY)
                groupHeaders.getOrPut(group) { Rect() }.set(x, by, rw, GROUP_HEADER_HEIGHT)
                by += GROUP_HEADER_HEIGHT + ROW_GAP
                if (gp.isVisible) {
                    val shown = stackHeight(ws) * gp
                    Render.pushScissor(x, by, rw, shown)
                    var gy = by
                    val gContentW = rw - LABEL_INDENT - GROUP_INDENT - Theme.PADDING
                    for (widget in ws) {
                        renderSettingRow(widget, x + LABEL_INDENT + GROUP_INDENT, gy, gContentW, mouseX, mouseY, query)
                        gy += widget.height + ROW_GAP
                    }
                    Render.popScissor()
                    by += shown
                }
            }

            Render.popScissor()
        }
    }

    private fun renderSettingRow(
        widget: SettingWidget,
        rx: Float, ry: Float, rw: Float,
        mouseX: Float, mouseY: Float,
        query: String?,
    ) {
        drawnWidgets += widget
        val wx = rx + rw - widget.widgetWidth

        // Badge on the right, next to the control, where the eye is.
        val badge = widget.setting.unsafeLabel
        val fade = if (badge == null) 0f else {
            val anim = unsafeAnims.getOrPut(widget.setting.id) { Animation(0f, Theme.ANIM_FAST.toLong(), Easing.easeOutCubic) }
            anim.animateTo(if (widget.setting.isUnsafe) 1f else 0f)
            anim.get()
        }
        val badgeW = if (badge == null || !fade.isVisible) 0f else {
            val w = (Render.textWidth(badge, BADGE_FONT, Fonts.bold) + Theme.PADDING_SMALL) * fade
            Render.text(
                badge, wx - w,
                Render.centerY(ry, Theme.SETTING_ROW_HEIGHT, BADGE_FONT, Fonts.bold),
                BADGE_FONT, Theme.DANGER.withAlpha(fade), Fonts.bold,
            )
            w
        }

        val full = widget.setting.translatedName
        val labelRoom = wx - badgeW - rx - Theme.PADDING
        val label = Render.ellipsize(full, labelRoom, Theme.FONT_SMALL, Fonts.regular)

        // Hover anywhere on the row shows the description. A widget's tooltip still wins.
        val overRow = hit(mouseX, mouseY, rx, ry, rw, widget.height)
        if (overRow) {
            val reason = widget.setting.translatedUnsafeReason?.takeIf { widget.setting.isUnsafe }
            val hint = widget.setting.translatedDescription
            val text = listOfNotNull(reason, hint, widget.inputHint).joinToString("\n")
                .ifEmpty { if (label != full) full else "" }
            if (text.isNotEmpty()) Tooltip.show(text)
        }

        renderHighlighted(
            label,
            query,
            rx,
            Render.centerY(ry, Theme.SETTING_ROW_HEIGHT, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, Theme.TEXT_SECONDARY, Fonts.regular,
        )
        widget.render(wx, ry, mouseX, mouseY)
    }

    private fun renderFeatureToggle(x: Float, y: Float) {
        // Always-active features get no pill, because a locked pill looks broken. Clicking still expands.
        if (feature.isAlwaysActive) return
        toggleAnim.animateTo(if (feature.enabled) 1f else 0f)
        Render.toggleSwitch(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, toggleAnim.get())
    }

    private fun drawGroupHeader(
        group: String, x: Float, y: Float, w: Float, progress: Float,
        labels: List<FeatureLabels.Shown>, mouseX: Float, mouseY: Float,
    ) {
        val cy = y + GROUP_HEADER_HEIGHT / 2f
        drawVChevron(x + 3f, cy, 3.5f, 1.3f, progress)
        val labelX = x + 12f
        Render.text(
            group,
            labelX,
            Render.centerY(y, GROUP_HEADER_HEIGHT, TextStyle.SMALL_SECONDARY_MEDIUM),
            TextStyle.SMALL_SECONDARY_MEDIUM,
        )
        val labelW = Render.textWidth(group, Theme.FONT_SMALL, Fonts.medium)
        val badgesRight = drawLabels(labels, labelX + labelW + 6f, y, GROUP_HEADER_HEIGHT, mouseX, mouseY)
        val lineX = badgesRight + 6f
        val lineRight = x + w
        if (lineRight > lineX) Render.rect(lineX, cy, lineRight - lineX, 1f, Theme.BORDER)
    }

    /** Right at 0, down at 1. The dropdown uses it too. */
    private fun drawVChevron(cx: Float, cy: Float, size: Float, thickness: Float, progress: Float) =
        Chevron.draw(cx, cy, size, thickness, progress)

    private fun drawChevron(rx: Float, ry: Float, expandT: Float) {
        val size = 5f
        val dxHalf = cos(Math.toRadians(45.0)).toFloat() * size / 2f
        drawVChevron(rx + Theme.PADDING + dxHalf, ry + COLLAPSED_HEIGHT / 2f, size, 1.6f, expandT)
    }

    /** One badge per kind, most serious first. Hover lists every reason. Returns the end x. */
    private fun drawLabels(labels: List<FeatureLabels.Shown>, x: Float, y: Float, h: Float, mouseX: Float, mouseY: Float): Float {
        if (labels.isEmpty()) return x
        var bx = x
        for (kind in LABEL_ORDER) {
            if (labels.none { it.label.kind == kind }) continue
            Render.text(kind.name, bx, Render.centerY(y, h, BADGE_FONT, Fonts.bold), BADGE_FONT, kind.color, Fonts.bold)
            bx += Render.textWidth(kind.name, BADGE_FONT, Fonts.bold) + Theme.PADDING_SMALL
        }
        if (hit(mouseX, mouseY, x, y, bx - x, h)) Tooltip.show(labels.joinToString("\n", transform = ::describe))
        return bx - Theme.PADDING_SMALL
    }

    private fun describe(shown: FeatureLabels.Shown): String {
        val head = shown.extension?.let { "${extensionTitle(it)} - ${shown.label.kind.name}" } ?: shown.label.kind.name
        return shown.reason?.let { "$head: $it" } ?: head
    }

    // Extensions have no name, so use their settings' subcategory.
    private fun extensionTitle(extension: FeatureExtension): String =
        extension.configSettings.firstNotNullOfOrNull { it.group }?.let(::translatedSettingGroup) ?: extension.id

    private fun extensionsIn(widgets: List<SettingWidget>): List<FeatureExtension> =
        FeatureManager.extensionsOf(feature).filter { ext -> widgets.any { it.setting in ext.configSettings } }

    // Enabling a broken feature is allowed, but never silently.
    private fun warnIfBroken() {
        val broken = FeatureLabels.onRow(feature).filter { it.label.kind == FeatureLabel.Kind.BROKEN }
        if (broken.isEmpty()) return
        Toasts.show(
            I18n.trOr("blackaddons.feature_label.broken_toast", "%s is marked broken right now", feature.translatedName),
            broken.mapNotNull { it.reason }.joinToString("\n"),
            ToastKind.ERROR,
        )
    }

    fun overlaying(): SettingWidget? = drawnWidgets.firstOrNull { it.isOverlaying }

    fun renderOverlays(mouseX: Float, mouseY: Float) = drawnWidgets.forEach { it.renderOverlay(mouseX, mouseY) }

    fun mouseClicked(mouse: MouseButtonInput, rx: Float, ry: Float, rw: Float, query: String?): Boolean {
        // Settings first, because they sit below the header.
        if (isOpen(query)) {
            val header = groupHeaders.entries.firstOrNull { it.value.has(mouse.x, mouse.y) }
            if (mouse.isLeft && header != null) {
                toggleGroup(header.key)
                return true
            }
            for (widget in drawnWidgets) if (widget.mouseClicked(mouse)) return true
        }
        if (hit(mouse.x, mouse.y, rx, ry, rw, COLLAPSED_HEIGHT)) {
            val tx = rx + rw - Theme.PADDING - TOGGLE_WIDTH
            val ty = centered(ry, COLLAPSED_HEIGHT, TOGGLE_HEIGHT)
            val hitToggle = mouse.isLeft && hit(mouse.x, mouse.y, tx, ty, TOGGLE_WIDTH, TOGGLE_HEIGHT)
            if (hitToggle && !feature.isAlwaysActive) {
                feature.toggle()
                if (feature.enabled) warnIfBroken()
            } else if (mouse.isRight) {
                FeatureMenu.entries(feature).takeIf { it.isNotEmpty() }?.let(UiManager::contextMenu)
            } else if (mouse.isLeft) {
                // A row click (or a locked @AlwaysActive toggle) expands.
                expanded = !expanded
                expandAnim.animateTo(if (expanded) 1f else 0f)
            }
            return true
        }
        return false
    }

    fun mouseScrolled(wheel: MouseScrollInput, query: String?): Boolean {
        if (!isOpen(query)) return false
        for (widget in drawnWidgets) if (widget.mouseScrolled(wheel)) return true
        return false
    }

    fun mouseReleased(mouse: MouseButtonInput): Boolean {
        var consumed = false
        for (widget in settings.visible()) if (widget.mouseReleased(mouse)) consumed = true
        return consumed
    }

    fun mouseDragged(drag: MouseDragInput): Boolean {
        for (widget in settings.visible()) if (widget.mouseDragged(drag)) return true
        return false
    }

    fun keyPressed(key: KeyInput): Boolean {
        for (widget in settings.visible()) if (widget.keyPressed(key)) return true
        return false
    }

    fun charTyped(typed: CharInput): Boolean {
        for (widget in settings.visible()) if (widget.charTyped(typed)) return true
        return false
    }

    private companion object {
        val COLLAPSED_HEIGHT = Theme.FEATURE_ROW_HEIGHT
        const val SETTINGS_BLOCK_PAD = 6f
        const val ROW_GAP = 2f

        /** Smaller than labels: an annotation, not a value. */
        const val BADGE_FONT = 8f
        const val LABEL_INDENT = 28f
        const val TOGGLE_WIDTH = 26f
        const val TOGGLE_HEIGHT = 14f
        const val GROUP_INDENT = 12f

        val LABEL_ORDER = listOf(FeatureLabel.Kind.BROKEN, FeatureLabel.Kind.UNSAFE, FeatureLabel.Kind.UNTESTED)
        val UNSAFE_COLOR = Color(255, 140, 40)
        val UNTESTED_COLOR = Color(230, 190, 60)

        val FeatureLabel.Kind.color: Color
            get() = when (this) {
                FeatureLabel.Kind.BROKEN -> Theme.DANGER
                FeatureLabel.Kind.UNSAFE -> UNSAFE_COLOR
                FeatureLabel.Kind.UNTESTED -> UNTESTED_COLOR
            }

        // A getter, because text metrics need the renderer, which gets wired after class load.
        val GROUP_HEADER_HEIGHT: Float get() = Render.textHeight(Theme.FONT_SMALL, Fonts.medium) + 8f
    }
}

private fun renderHighlighted(
    text: String,
    query: String?,
    x: Float,
    y: Float,
    size: Float,
    color: Color,
    font: Font,
) {
    val q = query?.trim()?.takeIf { it.isNotEmpty() }
    if (q == null) {
        Render.text(text, x, y, size, color, font)
        return
    }
    // Matched in place, because lowercasing can change length ('İ' becomes two chars) and break the indices.
    val idx = text.indexOf(q, ignoreCase = true)
    if (idx < 0) {
        Render.text(text, x, y, size, color, font)
        return
    }
    val before = text.substring(0, idx)
    val match = text.substring(idx, idx + q.length)
    val after = text.substring(idx + q.length)
    var cx = x
    if (before.isNotEmpty()) {
        Render.text(before, cx, y, size, color, font)
        cx += Render.textWidth(before, size, font)
    }
    Render.text(match, cx, y, size, Theme.ACCENT, Fonts.bold)
    cx += Render.textWidth(match, size, Fonts.bold)
    if (after.isNotEmpty()) {
        Render.text(after, cx, y, size, color, font)
    }
}
