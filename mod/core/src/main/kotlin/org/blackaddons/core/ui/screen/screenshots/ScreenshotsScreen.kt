package org.blackaddons.core.ui.screen.screenshots

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Image
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ConfirmPopover
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.screenshots.Screenshot
import org.blackaddons.core.screenshots.ScreenshotLibrary
import org.blackaddons.core.ui.screen.chat.ChatScreen
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * In-game screenshot browser: a preview grid and a full-size view with send, open, copy, star and delete. Opens on
 * [focus] if given (the notice after a screenshot).
 */
internal class ScreenshotsScreen(private var focus: Screenshot? = null) : ModalPanel(tr("title", "Screenshots")) {

    override val panelWidth: Float get() = (UiManager.screenWidth * 0.8f).coerceIn(MIN_W, MAX_W)
    override val panelHeight: Float get() = (UiManager.screenHeight * 0.82f).coerceIn(MIN_H, MAX_H)

    override val modalTitle: String
        get() = focus?.let { "${tr("title", "Screenshots")} - ${it.name}" } ?: tr("title", "Screenshots")

    @Volatile
    private var shots: List<Screenshot>? = null

    private var favoritesOnly = false

    // Held as a handle, so leaving frees the decoded pixels.
    private var fullImage: Image? = null

    private val allButton = widgets.add(Button(tr("all", "All")) { filter(false) })
    private val favoritesButton = widgets.add(Button(tr("favorites", "Starred")) { filter(true) })
    private val folderButton = widgets.add(Button(tr("folder", "Open folder")) { Platform.desktop.openPath(ScreenshotLibrary.folder) })

    private val backButton = widgets.add(Button(tr("back", "Back")) { unfocus() })
    private val previousButton = widgets.add(Button("<") { step(-1) })
    private val nextButton = widgets.add(Button(">") { step(1) })
    private val sendButton = widgets.add(Button(tr("send", "Send to chat")) { focus?.let { ChatScreen.openWith(listOf(it.path)) } }).apply { accent = true }
    private val openButton = widgets.add(Button(tr("open", "Open")) { focus?.let { Platform.desktop.openPath(it.path) } })
    private val copyButton = widgets.add(Button(tr("copy", "Copy")) { focus?.let(ScreenshotLibrary::copyToClipboard) })
    private val starButton = widgets.add(Button("") { focus?.let(ScreenshotLibrary::toggleFavorite) })
    private val deleteButton = widgets.add(Button(tr("delete", "Delete")) { confirmDelete() }).apply { danger = true }

    private var tileW = TILE_MIN_W

    private val grid = widgets.add(ScrollableList<List<Screenshot>>().apply {
        rowGap = 0f
        contentInsetY = ScrollableList.SIDE_INSET
        drawBackground = false
        drawRow = { row, _, rx, ry, _ ->
            for ((i, shot) in row.withIndex()) drawTile(shot, rx + i * tileW, ry)
        }
    })

    init {
        // Esc goes back to the grid first, then closes.
        keys.on(Keys.ESCAPE) {
            if (focus != null) {
                unfocus()
            } else {
                onEscape()
                UiManager.pop()
            }
        }
        keys.on(Keys.LEFT, onlyIf = { focus != null }) { step(-1) }
        keys.on(Keys.RIGHT, onlyIf = { focus != null }) { step(1) }
    }

    private var deleteX = 0f
    private var deleteY = 0f

    override fun onShow() = reload()

    override fun onClose() = releaseImage()

    private fun reload() {
        OffThread.run(
            name = "screenshots-list",
            work = { ScreenshotLibrary.list() },
            onFailure = { emptyList() },
            onResult = { shots = it },
        )
    }

    private fun visible(): List<Screenshot> {
        val all = shots.orEmpty()
        return if (favoritesOnly) all.filter(ScreenshotLibrary::isFavorite) else all
    }

    private fun filter(favorites: Boolean) {
        favoritesOnly = favorites
        grid.resetScroll()
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        if (focus != null) renderFocus(mouseX, mouseY) else renderGrid(mouseX, mouseY)
    }

    private fun renderGrid(mouseX: Float, mouseY: Float) {
        val barH = BUTTON_H
        var x = contentX
        allButton.accent = !favoritesOnly
        favoritesButton.accent = favoritesOnly
        allButton.render(x, contentY, allButton.naturalWidth(), barH, mouseX, mouseY)
        x += allButton.naturalWidth() + Theme.PADDING_SMALL
        favoritesButton.render(x, contentY, favoritesButton.naturalWidth(), barH, mouseX, mouseY)
        x += favoritesButton.naturalWidth() + Theme.PADDING

        val list = visible()
        val loaded = shots != null
        if (loaded) {
            Render.text(
                tr("count", "%d screenshots").format(list.size), x,
                Render.centerY(contentY, barH, TextStyle.SMALL_MUTED),
                TextStyle.SMALL_MUTED,
            )
        }
        val folderW = folderButton.naturalWidth()
        folderButton.render(contentX + contentW - folderW, contentY, folderW, barH, mouseX, mouseY)

        val listY = contentY + barH + Theme.PADDING
        val listH = contentY + contentH - listY
        val rowW = contentW - ScrollableList.SIDE_INSET * 2f
        val columns = ((rowW + TILE_GAP) / TILE_MIN_W).toInt().coerceAtLeast(1)
        tileW = (rowW + TILE_GAP) / columns
        grid.rowHeight = (tileW - TILE_GAP) * THUMB_RATIO + LABEL_H + TILE_GAP
        grid.emptyMessage = when {
            !loaded -> tr("loading", "Loading...")
            favoritesOnly -> tr("no_favorites", "Nothing starred yet.")
            else -> tr("empty", "No screenshots yet. Press F2 in game.")
        }
        grid.items = list.chunked(columns)
        grid.render(contentX, listY, contentW, listH)
    }

    private fun drawTile(shot: Screenshot, x: Float, y: Float) {
        val w = tileW - TILE_GAP
        val imageH = w * THUMB_RATIO
        val hovered = widgets.clickable(x, y, w, imageH + LABEL_H) { focusOn(shot) }

        Render.rect(x, y, w, imageH, Theme.SURFACE, Theme.RADIUS_SMALL)
        ScreenshotLibrary.thumbnail(shot)?.let { Render.image(it, x, y, w, imageH, Theme.RADIUS_SMALL) }
        if (hovered) Render.hollowRect(x, y, w, imageH, 1.5f, Theme.ACCENT, Theme.RADIUS_SMALL)
        if (ScreenshotLibrary.isFavorite(shot)) {
            Render.rect(x + w - STAR_BOX - 4f, y + 4f, STAR_BOX, STAR_BOX, Theme.BACKGROUND.withAlpha(170), Theme.RADIUS_SMALL)
            Render.icon(LucideIcons.starFilled, x + w - STAR_BOX - 1f, y + 7f, STAR_BOX - 6f, STAR_COLOR)
        }

        Render.text(
            DATE.format(Instant.ofEpochMilli(shot.takenAt)), x, y + imageH + 3f, Theme.FONT_SMALL,
            if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY, Fonts.regular, maxWidth = w,
        )
    }

    private fun renderFocus(mouseX: Float, mouseY: Float) {
        val shot = focus ?: return
        val barY = contentY + contentH - BUTTON_H
        val areaH = barY - Theme.PADDING - contentY

        val aspect = ScreenshotLibrary.aspect(shot) ?: (1f / THUMB_RATIO)
        val (w, h) = if (contentW / areaH > aspect) areaH * aspect to areaH else contentW to contentW / aspect
        val ix = centered(contentX, contentW, w)
        val iy = centered(contentY, areaH, h)
        Render.rect(ix, iy, w, h, Theme.SURFACE, Theme.RADIUS_SMALL)
        val image = fullImage ?: Render.createImage(Render.FILE_SCHEME + shot.path.toAbsolutePath()).also { fullImage = it }
        Render.image(image, ix, iy, w, h, Theme.RADIUS_SMALL)

        var x = contentX
        for (button in listOf(backButton, previousButton, nextButton)) {
            val bw = button.naturalWidth().coerceAtLeast(BUTTON_H)
            button.render(x, barY, bw, BUTTON_H, mouseX, mouseY)
            x += bw + Theme.PADDING_SMALL
        }
        val list = visible()
        val index = list.indexOf(shot)
        previousButton.enabled = index > 0
        nextButton.enabled = index in 0 until list.lastIndex

        starButton.label = if (ScreenshotLibrary.isFavorite(shot)) tr("unfavorite", "Unstar") else tr("favorite", "Star")
        var right = contentX + contentW
        for (button in listOf(deleteButton, starButton, copyButton, openButton, sendButton)) {
            val bw = button.naturalWidth()
            right -= bw
            button.render(right, barY, bw, BUTTON_H, mouseX, mouseY)
            if (button === deleteButton) {
                deleteX = right
                deleteY = barY
            }
            right -= Theme.PADDING_SMALL
        }
    }

    private fun focusOn(shot: Screenshot?) {
        releaseImage()
        focus = shot
    }

    private fun unfocus() = focusOn(null)

    private fun step(by: Int) {
        val list = visible()
        val index = list.indexOf(focus ?: return)
        list.getOrNull(index + by)?.let(::focusOn)
    }

    private fun releaseImage() {
        fullImage?.let(Render::deleteImage)
        fullImage = null
    }

    private fun confirmDelete() {
        val shot = focus ?: return
        UiManager.push(
            ConfirmPopover(
                anchorX = deleteX, anchorY = deleteY,
                anchorW = deleteButton.naturalWidth(), anchorH = BUTTON_H,
                message = tr("delete_confirm", "Delete this screenshot for good?"),
                confirmLabel = tr("delete", "Delete"),
            ) {
                val list = visible()
                val index = list.indexOf(shot)
                if (!ScreenshotLibrary.delete(shot)) return@ConfirmPopover
                shots = shots.orEmpty() - shot
                val remaining = visible()
                focusOn(remaining.getOrNull(index.coerceAtMost(remaining.lastIndex)))
            },
        )
    }

    companion object {
        private val tr = I18n.scope("blackaddons.screenshots")

        fun open(focus: Screenshot? = null) = UiManager.open(ScreenshotsScreen(focus))

        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

        private const val MIN_W = 480f
        private const val MAX_W = 1100f
        private const val MIN_H = 320f
        private const val MAX_H = 760f

        private const val BUTTON_H = 20f
        private const val TILE_MIN_W = 150f
        private const val TILE_GAP = 8f
        private const val LABEL_H = 16f
        private const val STAR_BOX = 16f
        private val STAR_COLOR = Color(250, 204, 21)
        private val THUMB_RATIO = ScreenshotLibrary.THUMB_H.toFloat() / ScreenshotLibrary.THUMB_W
    }
}
