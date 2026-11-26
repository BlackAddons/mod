package org.blackaddons.core.ui.screen.portable

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.portable.PortableExportModal.Companion.MAX_LINES
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Shows the exported string, not a toast. Toasts draw under open screens, and
 * [org.blackaddons.api.host.ClipboardHost.copy] fails silently, so the string itself is the proof. Grows to
 * [MAX_LINES], then scrolls. A click selects all. Ctrl+A/C work.
 */
internal class PortableExportModal(
    title: String,
    private val headline: String,
    private val exported: String,
) : ModalPanel(title) {

    override val panelWidth: Float get() = fitWidth(420f, margin = 24f)
    override val panelHeight: Float
        get() {
            val w = panelWidth - Theme.PADDING_LARGE * 2f
            return minOf(heightFor(headerHeight(w) + boxHeight(w)), UiManager.screenHeight - 24f).coerceAtLeast(0f)
        }

    private val hint get() = PortableImports.tr("share_hint", "They import it from the same menu, or with /b import.")

    private var copiedAt: TimeSource.Monotonic.ValueTimeMark? = null
    private var selected = false

    // Wrapped once per width, because each measurement is a text layout.
    private var lines: List<String> = emptyList()
    private var wrappedFor = 0f

    private val lineList = widgets.add(ScrollableList<String>().apply {
        rowGap = 0f
        drawBackground = false
        drawRow = { line, _, x, y, _ -> drawLine(line, x, y) }
    })

    private val copyButton = widgets.add(Button(copyLabel()) { copy() })
    private val doneButton = widgets.add(Button(PortableImports.tr("close", "Close")) { UiManager.pop() }.apply { accent = true })

    init {
        copy()
        keys.on(Keys.A, ctrl = true) { selected = true }
        keys.on(Keys.C, ctrl = true) {
            selected = true
            copy()
        }
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        Render.text(
            headline,
            contentX, contentY, TextStyle.BODY_PRIMARY_MEDIUM, maxWidth = contentW,
        )
        Render.wrappedText(
            hint, contentX, contentY + Theme.FONT_BODY + HINT_GAP, contentW,
            TextStyle.SMALL_MUTED, HINT_LINE_STEP,
        )

        val boxY = contentY + headerHeight(contentW)
        val boxH = (footerTop - ModalPanel.FOOTER_GAP - boxY).coerceAtLeast(0f)
        Render.rect(contentX, boxY, contentW, boxH, Theme.SURFACE.withAlpha(150), Theme.RADIUS_SMALL)
        Render.hollowRect(contentX, boxY, contentW, boxH, 1f, if (selected) Theme.ACCENT.withAlpha(160) else Theme.BORDER, Theme.RADIUS_SMALL)
        widgets.clickable(contentX, boxY, contentW, boxH) { selected = true }

        lineList.rowHeight = lineStep()
        lineList.items = wrapped(textWidth(contentW))
        lineList.render(contentX, boxY + BOX_PAD, contentW, boxH - BOX_PAD * 2f)

        copyButton.label = copyLabel()
        footer(mouseX, mouseY, doneButton, leading = copyButton)
    }

    private fun copyLabel(): String {
        val fresh = copiedAt?.let { it.elapsedNow() < CONFIRM } == true
        return PortableImports.tr(if (fresh) "copied_mark" else "copy_again", if (fresh) "Copied" else "Copy again")
    }

    override fun contentClicked(mouse: MouseButtonInput): Boolean {
        selected = false
        return super.contentClicked(mouse)
    }

    private fun drawLine(line: String, x: Float, y: Float) {
        val h = lineList.rowHeight
        if (selected) Render.rect(x, y, Render.textWidth(line, Theme.FONT_SMALL, Fonts.regular), h, Theme.ACCENT.withAlpha(70))
        Render.text(
            line, x, Render.centerY(y, h, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, if (selected) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY, Fonts.regular,
        )
    }

    private fun headerHeight(w: Float): Float =
        Theme.FONT_BODY + HINT_GAP +
            Render.wrappedTextHeight(hint, w, Theme.FONT_SMALL, Fonts.regular, HINT_LINE_STEP) + Theme.PADDING

    private fun textWidth(boxW: Float) = boxW - ScrollableList.SIDE_INSET * 2f

    /** The string has no spaces, so word wrapping can't break it. [Render.wrapText] splits long words. */
    private fun wrapped(w: Float): List<String> {
        if (wrappedFor != w) {
            lines = Render.wrapText(exported, w, Theme.FONT_SMALL, Fonts.regular)
            wrappedFor = w
        }
        return lines
    }

    private fun lineStep() = Render.textHeight(Theme.FONT_SMALL, Fonts.regular) * LINE_STEP

    private fun boxHeight(boxW: Float): Float {
        val shown = wrapped(textWidth(boxW)).size.coerceIn(1, MAX_LINES)
        return shown * lineStep() + BOX_PAD * 2f
    }

    private fun copy() {
        Platform.clipboard.copy(exported)
        copiedAt = TimeSource.Monotonic.markNow()
    }

    companion object {
        private const val LINE_STEP = 1.35f
        private const val HINT_LINE_STEP = 1.3f
        private const val HINT_GAP = 4f
        private val CONFIRM = 1600.milliseconds
        private const val MAX_LINES = 8
        private const val BOX_PAD = 6f

        fun share(label: String, exported: String, over: Boolean = false) {
            val modal = PortableExportModal(
                PortableImports.tr("share", "Share"),
                PortableImports.tr("shared", "%s is on your clipboard.", label),
                exported,
            )
            if (over) UiManager.overlay(modal) else UiManager.push(modal)
        }
    }
}
