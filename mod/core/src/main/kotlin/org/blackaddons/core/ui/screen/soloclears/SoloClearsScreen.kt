package org.blackaddons.core.ui.screen.soloclears

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.hovered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.LatestOnly
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.soloclears.Board
import org.blackaddons.core.soloclears.BoardEntry
import org.blackaddons.core.soloclears.MyRun
import org.blackaddons.core.soloclears.SoloClearBoard
import java.awt.Color

private val tr = I18n.scope("blackaddons.soloclears")

/**
 * Solo clears boards plus the player's own runs. Three boards per floor: a run is on overall and exactly one half,
 * because "fastest" and "fastest without skipping a puzzle" have different winners. The runs tab shows why a run didn't
 * get on a board.
 */
internal class SoloClearsScreen : ModalPanel(tr("title", "Solo Clears")) {

    override val panelWidth = 520f
    override val panelHeight = 400f

    private var tab = SoloClearBoard.FLOORS.first()
    private var category = SoloClearBoard.OVERALL

    @Volatile
    private var board: Board? = null

    @Volatile
    private var runs: List<MyRun>? = null

    /** "Still asking" and "nothing" show differently. */
    @Volatile
    private var loading = true

    private val floorButtons = SoloClearBoard.FLOORS.map { floor ->
        Button(floor) { select(floor) }
    }
    private val runsButton = Button(tr("mine", "My runs")) { select(MINE) }
    private val categoryButtons = SoloClearBoard.CATEGORIES.map { name ->
        Button(categoryLabel(name)) { pick(name) }
    }

    private val entries = ScrollableList<BoardEntry>().apply {
        rowHeight = 22f
        rowGap = 1f
        drawRow = { entry, _, x, y, w -> BoardRow.draw(entry, x, y, w, rowHeight) }
    }
    private val myRuns = ScrollableList<MyRun>().apply {
        rowHeight = 30f
        rowGap = 1f
        drawRow = { run, _, x, y, w -> MyRunRow.draw(run, x, y, w, rowHeight) }
    }

    init {
        // Controls only answer while their tab draws them.
        floorButtons.forEach(widgets::add)
        widgets.add(runsButton)
        categoryButtons.forEach(widgets::add)
        widgets.add(entries)
        widgets.add(myRuns)
    }

    override fun onShow() {
        BackendNotifier.ensureConnected()
        load()
    }

    private fun select(next: String) {
        if (tab == next) return
        tab = next
        entries.resetScroll()
        myRuns.resetScroll()
        load()
    }

    private fun pick(next: String) {
        if (category == next) return
        category = next
        entries.resetScroll()
        load()
    }

    // A late answer for an older floor or category gets dropped.
    private val lookups = LatestOnly()

    /** Off-thread. A failure shows as empty instead of staying on loading. */
    private fun load() {
        loading = true
        board = null
        runs = null

        if (tab == MINE) {
            OffThread.run(
                name = "soloclears-mine",
                work = { SoloClearBoard.mine() },
                onFailure = { null },
                onResult = lookups.next { result -> runs = result.orEmpty(); loading = false },
            )
            return
        }

        val floor = tab
        val wanted = category
        OffThread.run(
            name = "soloclears-board",
            work = { SoloClearBoard.board(floor, wanted) },
            onFailure = { null },
            onResult = lookups.next { result -> board = result; loading = false },
        )
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        var x = contentX
        for ((index, button) in floorButtons.withIndex()) {
            button.accent = tab == SoloClearBoard.FLOORS[index]
            button.render(x, contentY, TAB_W, TAB_H, mouseX, mouseY)
            x += TAB_W + Theme.PADDING_SMALL
        }
        runsButton.accent = tab == MINE
        runsButton.render(x, contentY, MINE_W, TAB_H, mouseX, mouseY)

        var listY = contentY + TAB_H + Theme.PADDING

        if (tab != MINE) {
            var cx = contentX
            for ((index, button) in categoryButtons.withIndex()) {
                button.accent = category == SoloClearBoard.CATEGORIES[index]
                button.render(cx, listY, CATEGORY_W, TAB_H, mouseX, mouseY)
                cx += CATEGORY_W + Theme.PADDING_SMALL
            }
            listY += TAB_H + Theme.PADDING
        }

        val listH = contentY + contentH - listY
        if (tab == MINE) renderRuns(listY, listH) else renderBoard(listY, listH)
    }

    private fun renderBoard(y: Float, h: Float) {
        BoardRow.header(contentX, y, contentW)
        val rows = board?.entries.orEmpty()
        entries.items = rows
        // "Nobody yet" would be a lie when the server didn't answer.
        entries.emptyMessage = when {
            loading -> tr("loading", "Loading...")
            board == null -> tr("board_unavailable", "No board for this floor yet.")
            else -> tr("board_empty", "Nobody has a clear on this board.")
        }
        entries.render(contentX, y + HEADER_H, contentW, h - HEADER_H)
    }

    private fun renderRuns(y: Float, h: Float) {
        myRuns.items = runs.orEmpty()
        myRuns.emptyMessage = when {
            loading -> tr("loading", "Loading...")
            runs == null -> tr("mine_unavailable", "Could not read your runs.")
            else -> tr("mine_empty", "You have not handed in a run yet.")
        }
        myRuns.render(contentX, y, contentW, h)
    }

    private fun categoryLabel(name: String): String = when (name) {
        SoloClearBoard.NO_SKIPS -> tr("no_skips", "No skips")
        SoloClearBoard.WITH_SKIPS -> tr("with_skips", "With skips")
        else -> tr("overall", "Overall")
    }

    companion object {
        fun open() = UiManager.open(SoloClearsScreen())

        private const val MINE = "mine"
        private const val TAB_W = 48f
        private const val MINE_W = 70f
        private const val TAB_H = 20f
        private const val CATEGORY_W = 82f
        private const val HEADER_H = 14f
    }
}

private object BoardRow {

    fun header(x: Float, y: Float, w: Float) {
        Render.text("#", x + 8f, y, FONT_TINY, Theme.TEXT_MUTED, Fonts.medium)
        Render.text(
            tr("column_player", "Player"),
            x + 30f, y, FONT_TINY, Theme.TEXT_MUTED, Fonts.medium,
        )
        right(tr("column_score", "Score"), x + w - 14f, y, Theme.TEXT_MUTED)
        right(tr("column_time", "Time"), x + w - 66f, y, Theme.TEXT_MUTED)
    }

    fun draw(entry: BoardEntry, x: Float, y: Float, w: Float, h: Float) {
        if (hovered(x, y, w, h)) {
            Render.rect(x, y, w, h, Theme.SURFACE_HOVER.withAlpha(120), Theme.RADIUS_SMALL)
            entry.dungeonClass?.let { Tooltip.show(it) }
        }

        val textY = Render.centerY(y, h, Theme.FONT_SMALL, Fonts.regular)
        Render.text("${entry.position}", x + 8f, textY, Theme.FONT_SMALL, medal(entry.position), Fonts.medium)

        val name = entry.name ?: tr("unknown_player", "Unknown")
        val self = name.equals(Platform.player.name, ignoreCase = true)
        Render.text(
            name, x + 30f, textY, Theme.FONT_SMALL,
            if (self) Theme.ACCENT else Theme.TEXT_PRIMARY,
            if (self) Fonts.medium else Fonts.regular,
            maxWidth = w - 150f,
        )

        right(RunTime.of(entry.elapsedSeconds), x + w - 66f, textY, Theme.TEXT_PRIMARY)
        right("${entry.score}", x + w - 14f, textY, Theme.TEXT_SECONDARY)
    }

    private fun medal(position: Int): Color = when (position) {
        1 -> Color(0xFFCC44)
        2 -> Color(0xD0D0D8)
        3 -> Color(0xCC8844)
        else -> Theme.TEXT_MUTED
    }

    fun right(text: String, rightEdge: Float, y: Float, color: Color) {
        val font = Fonts.regular
        val width = Render.textWidth(text, Theme.FONT_SMALL, font)
        Render.text(text, rightEdge - width, y, Theme.FONT_SMALL, color, font)
    }
}

private object MyRunRow {

    fun draw(run: MyRun, x: Float, y: Float, w: Float, h: Float) {
        val hovered = hovered(x, y, w, h)
        if (hovered) Render.rect(x, y, w, h, Theme.SURFACE_HOVER.withAlpha(120), Theme.RADIUS_SMALL)

        Render.text(run.floor, x + 8f, y + 5f, TextStyle.SMALL_PRIMARY_MEDIUM)
        Render.text(
            RunTime.of(run.elapsedSeconds),
            x + 42f, y + 5f, TextStyle.SMALL_PRIMARY,
        )
        Render.text(
            "${run.score}",
            x + 92f, y + 5f, TextStyle.SMALL_SECONDARY,
        )

        val status = run.status
        Render.text(
            status, x + w - 8f - Render.textWidth(status, Theme.FONT_SMALL, Fonts.medium), y + 5f,
            Theme.FONT_SMALL, statusColor(status), Fonts.medium,
        )

        // This answers "why isn't it on the board". A status alone doesn't.
        val detail = run.statusReason ?: run.flags.firstOrNull()
        if (detail != null) {
            Render.text(detail, x + 8f, y + 17f, FONT_TINY, Theme.TEXT_MUTED, Fonts.regular, maxWidth = w - 16f)
        }
        if (hovered && run.flags.size > 1) Tooltip.show(run.flags.joinToString("\n"))
    }

    private fun statusColor(status: String): Color = when (status) {
        "Listed" -> Theme.SUCCESS
        "Rejected", "Revoked" -> Theme.DANGER
        "PendingReview" -> WARNING
        else -> Theme.TEXT_SECONDARY
    }
}

private val WARNING = Color(0xFFCC44)

private const val FONT_TINY = 9f

private object RunTime {
    fun of(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
}
