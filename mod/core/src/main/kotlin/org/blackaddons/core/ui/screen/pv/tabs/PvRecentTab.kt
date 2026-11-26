package org.blackaddons.core.ui.screen.pv.tabs

import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.profile.SkyblockProfile
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.Cards
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.CosmeticName
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.*
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.profile.PlayerSkins
import org.blackaddons.core.profile.RecentActivity
import org.blackaddons.core.profile.TeammateHistory
import org.blackaddons.core.profile.dashless
import java.awt.Color
import java.time.Duration
import java.time.Instant
import java.util.Locale

private val tr = I18n.scope("blackaddons.pv.recent")

/** The profile's teammate history. Actions are only in the detail pane, so the list keeps one hitbox per row. */
internal class PvRecentTab(private val openProfile: (String) -> Unit) {

    private sealed interface DetailRow {
        data class Heading(val text: String) : DetailRow
        data class Best(val activity: String, val elapsedMillis: Long, val profilePbMillis: Long) : DetailRow
        data class RecentRun(val activity: String, val completedAt: Long, val role: String?) : DetailRow
        data class Message(val text: String) : DetailRow
    }

    private enum class TeammateOrder { RECENT, RUNS, NAME }

    private data class ActivityFilter(val key: String, val label: String)

    private data class ActivitySlice(val label: String, val count: Int, val color: Color)

    private data class Teammate(
        val uuid: String,
        val name: String?,
        val role: String?,
        val count: Int,
        val lastActivity: String,
        val lastSeen: Long,
    )

    val widgets = WidgetHost()
    private val list = widgets.add(ScrollableList<Teammate>().apply {
        rowHeight = ROW_H
        rowGap = ROW_GAP
        emptyMessage = tr("empty", "No recent teammates in this profile.")
        drawRow = ::drawTeammate
    })
    private val detailList = widgets.add(ScrollableList<DetailRow>().apply {
        rowHeight = DETAIL_ROW_H
        rowGap = 0f
        drawBackground = false
        drawRow = ::drawDetailRow
    })
    private val activityChart = widgets.add(DonutChart())
    private val orderDropdown = Dropdown().apply {
        items = TeammateOrder.entries.map { Dropdown.Item(orderLabel(it)) }
        onSelect = { order = TeammateOrder.entries[it] }
    }
    private val activityDropdown = Dropdown(maxVisibleItems = 8).apply {
        onSelect = { index ->
            filter = activityFilters.getOrNull(index)?.key ?: ALL_ACTIVITY
            list.resetScroll()
            detailList.resetScroll()
            chartReveal.set(0f)
        }
    }

    init {
        widgets.add(orderDropdown)
        widgets.add(activityDropdown)
    }

    private val open = widgets.add(Button(tr("open", "Open Profile")) {
        selected()?.name?.let(openProfile)
    })
    private val invite = widgets.add(Button(tr("invite", "Invite to Party")) {
        selected()?.name?.takeIf { it.matches(IGN) }?.let { Platform.chat.sendCommand("party invite $it") }
    })

    private var entries = emptyList<Teammate>()
    private var selectedUuid: String? = null
    private var order = TeammateOrder.RECENT
    private var filter = ALL_ACTIVITY
    private var activityFilters = emptyList<ActivityFilter>()
    private val chartReveal = Animation(durationMs = Theme.ANIM_NORMAL.toLong())

    fun reset() {
        entries = emptyList()
        selectedUuid = null
        list.items = emptyList()
        list.resetScroll()
        list.clear()
        detailList.items = emptyList()
        detailList.resetScroll()
        detailList.clear()
        activityChart.segments = emptyList()
        orderDropdown.closeImmediately()
        activityDropdown.closeImmediately()
        chartReveal.set(0f)
        open.clear()
        invite.clear()
    }

    fun hide() = reset()

    fun render(x: Float, y: Float, width: Float, height: Float, ownerUuid: String, profile: SkyblockProfile) {
        val activities = TeammateHistory.activities(ownerUuid, profile.id)
        updateActivityFilters(activities)
        chartReveal.animateTo(1f)
        val filteredActivities = activities.filter(::matchesFilter)
        entries = teammates(ownerUuid, filteredActivities)
        if (entries.none { it.uuid == selectedUuid }) selectedUuid = entries.firstOrNull()?.uuid
        list.items = entries

        val orderW = orderDropdown.preferredWidth().coerceAtLeast(ORDER_MIN_W)
        val filterW = FILTER_W.coerceAtMost((width - orderW - Theme.PADDING_SMALL).coerceAtLeast(0f))
        val controlsX = x + width - filterW - orderW - Theme.PADDING_SMALL
        Render.text(
            tr("title", "Recent Teammates").uppercase(), x, Render.centerY(y, CONTROL_H, TextStyle.SMALL_SECONDARY_BOLD),
            TextStyle.SMALL_SECONDARY_BOLD, maxWidth = controlsX - x - Theme.PADDING,
        )
        orderDropdown.render(controlsX, y, orderW, CONTROL_H, UiMouse.x, UiMouse.y)
        activityDropdown.render(x + width - filterW, y, filterW, CONTROL_H, UiMouse.x, UiMouse.y)

        val top = y + CONTROL_H + Cards.GAP
        val gap = Cards.GAP
        val listW = (width * LIST_PORTION).coerceAtLeast(MIN_LIST_W)
        val detailX = x + listW + gap
        val detailW = width - listW - gap
        val contentH = (height - CONTROL_H - Cards.GAP).coerceAtLeast(0f)
        list.render(x, top, listW, contentH)
        drawDetail(detailX, top, detailW, contentH, profile, filteredActivities)
    }

    private fun teammates(ownerUuid: String, activities: List<RecentActivity>): List<Teammate> = activities
        .flatMap { activity ->
            activity.participants
                .filterNot { sameUuid(it.uuid, ownerUuid) }
                .map { participant -> activity to participant }
        }
        .groupBy { (_, participant) -> participant.uuid.lowercase(Locale.ROOT) }
        .map { (uuid, appearances) ->
            val newest = appearances.maxBy { (activity, _) -> activity.completedAt }
            Teammate(
                uuid = uuid,
                name = newest.second.name,
                role = newest.second.role,
                count = appearances.map { it.first.id }.toSet().size,
                lastActivity = newest.first.activity,
                lastSeen = newest.first.completedAt,
            )
        }
        .sortedWith(
            when (order) {
                TeammateOrder.RECENT -> compareByDescending<Teammate> { it.lastSeen }.thenBy { it.name ?: it.uuid }
                TeammateOrder.RUNS -> compareByDescending<Teammate> { it.count }.thenByDescending { it.lastSeen }
                TeammateOrder.NAME -> compareBy<Teammate> { it.name ?: it.uuid }
            },
        )

    private fun updateActivityFilters(activities: List<RecentActivity>) {
        val available = activities.map(RecentActivity::activity).toSortedSet()
        activityFilters = buildList {
            add(ActivityFilter(ALL_ACTIVITY, tr("filter_all", "All activity")))
            if (activities.any { !it.kuudra && !it.activity.startsWith("M") }) {
                add(ActivityFilter(DUNGEON_ACTIVITY, tr("filter_dungeon", "Dungeon")))
            }
            if (activities.any { it.activity.startsWith("M") }) {
                add(ActivityFilter(MASTER_ACTIVITY, tr("filter_master", "Master Mode")))
            }
            if (activities.any(RecentActivity::kuudra)) {
                add(ActivityFilter(KUUDRA_ACTIVITY, tr("filter_kuudra", "Kuudra")))
            }
            available.forEach { add(ActivityFilter("activity:$it", it)) }
        }
        if (activityFilters.none { it.key == filter }) filter = ALL_ACTIVITY
        activityDropdown.items = activityFilters.map { Dropdown.Item(it.label) }
        activityDropdown.selectedIndex = activityFilters.indexOfFirst { it.key == filter }.coerceAtLeast(0)
    }

    private fun matchesFilter(activity: RecentActivity): Boolean = matchesFilter(activity.activity, activity.kuudra)

    private fun matchesFilter(activity: String, kuudra: Boolean): Boolean = when (filter) {
        ALL_ACTIVITY -> true
        DUNGEON_ACTIVITY -> !kuudra && !activity.startsWith("M")
        MASTER_ACTIVITY -> !kuudra && activity.startsWith("M")
        KUUDRA_ACTIVITY -> kuudra
        else -> activity == filter.removePrefix("activity:")
    }

    private fun orderLabel(order: TeammateOrder): String = when (order) {
        TeammateOrder.RECENT -> tr("sort_recent", "Latest")
        TeammateOrder.RUNS -> tr("sort_runs", "Most runs")
        TeammateOrder.NAME -> tr("sort_name", "Name")
    }

    private fun drawTeammate(teammate: Teammate, index: Int, x: Float, y: Float, width: Float) {
        val selected = teammate.uuid == selectedUuid
        val hovered = widgets.clickable(x, y, width, ROW_H) {
            if (selectedUuid != teammate.uuid) {
                selectedUuid = teammate.uuid
                detailList.resetScroll()
                chartReveal.set(0f)
            }
        }
        Render.rect(x, y, width, ROW_H, when {
            selected -> Theme.ACCENT_SOFT
            hovered -> Theme.SURFACE_HOVER
            else -> Theme.SURFACE
        }, Theme.RADIUS_SMALL)
        if (selected) Render.hollowRect(x, y, width, ROW_H, 1f, Theme.ACCENT, Theme.RADIUS_SMALL)

        val head = ROW_H - Theme.PADDING * 2f
        Render.playerHead(teammate.uuid, teammate.name ?: "?", PlayerSkins.texture(teammate.uuid), x + Theme.PADDING, y + Theme.PADDING, head, head)
        val textX = x + Theme.PADDING * 2f + head
        val count = teammate.count.toString()
        val countW = Render.textWidth(count, Theme.FONT_BODY, Fonts.medium)
        val textW = (x + width - Theme.PADDING - countW - Theme.PADDING - textX).coerceAtLeast(0f)
        val nameH = Render.textHeight(Theme.FONT_BODY, Fonts.medium)
        val detailH = Render.textHeight(Theme.FONT_SMALL, Fonts.regular)
        val nameY = y + (ROW_H - nameH - Theme.PADDING_SMALL - detailH) / 2f
        drawName(teammate, textX, nameY, TextStyle.BODY_PRIMARY_MEDIUM, textW)
        val detail = "${teammate.lastActivity} · ${relativeTime(teammate.lastSeen)}"
        Render.text(detail, textX, nameY + nameH + Theme.PADDING_SMALL, TextStyle.SMALL_SECONDARY, maxWidth = textW)
        Render.text(count, x + width - Theme.PADDING - countW, Render.centerY(y, ROW_H, Theme.FONT_BODY, Fonts.medium), Theme.FONT_BODY, Theme.ACCENT, Fonts.medium)
    }

    private fun drawName(teammate: Teammate, x: Float, y: Float, style: TextStyle, maxWidth: Float) {
        val name = teammate.name
        if (name == null) {
            Render.text(tr("unknown", "Unknown"), x, y, style, maxWidth = maxWidth)
            return
        }
        val cosmetic = CosmeticName.ellipsize(
            Platform.cosmetics.nameFor(Uuids.parse(teammate.uuid)), name, maxWidth, style.size, style.font,
        )
        CosmeticName.draw(cosmetic, name, x, y, style.size, style.font, style.color)
    }

    private fun drawDetail(x: Float, y: Float, width: Float, height: Float, profile: SkyblockProfile, activities: List<RecentActivity>) {
        val teammate = selected()
        if (teammate == null) {
            detailList.clear()
            Cards.drawCard(x, y, width, height)
            return
        }
        Cards.drawCard(x, y, width, height)
        val pad = Theme.PADDING
        val head = 34f
        Render.playerHead(teammate.uuid, teammate.name ?: "?", PlayerSkins.texture(teammate.uuid), x + pad, y + pad, head, head)
        val textX = x + pad * 2f + head
        drawName(teammate, textX, y + pad, TextStyle.TITLE_PRIMARY_MEDIUM, width - (textX - x) - pad)
        val summary = listOfNotNull(teammate.role, runCount(teammate.count)).joinToString(" · ")
        Render.text(summary, textX, y + pad + Theme.FONT_TITLE + Theme.PADDING_SMALL, TextStyle.SMALL_SECONDARY, maxWidth = width - (textX - x) - pad)

        val actionY = y + pad + head + Theme.PADDING
        val buttonW = (width - pad * 3f) / 2f
        open.enabled = teammate.name != null
        invite.enabled = teammate.name?.matches(IGN) == true
        open.render(x + pad, actionY, buttonW, Button.DEFAULT_HEIGHT, UiMouse.x, UiMouse.y)
        invite.render(x + pad * 2f + buttonW, actionY, buttonW, Button.DEFAULT_HEIGHT, UiMouse.x, UiMouse.y)
        if (!invite.enabled && UiMouse.x in (x + pad * 2f + buttonW)..(x + width - pad) && UiMouse.y in actionY..(actionY + Button.DEFAULT_HEIGHT)) {
            Tooltip.show(tr("invite_unavailable", "No safe player name was available for this teammate."))
        }

        val personalRuns = sharedRuns(teammate, activities)
        val breakdown = activityBreakdown(personalRuns)
        val chartY = actionY + Button.DEFAULT_HEIGHT + Theme.PADDING_LARGE
        val chartH = drawActivityBreakdown(breakdown, x + pad, chartY, width - pad * 2f)
        detailList.items = detailRows(teammate, profile, personalRuns)
        val detailY = chartY + chartH + Theme.PADDING
        detailList.render(x + pad, detailY, width - pad * 2f, (y + height - pad - detailY).coerceAtLeast(0f))
    }

    private fun detailRows(teammate: Teammate, profile: SkyblockProfile, personalRuns: List<RecentActivity>): List<DetailRow> {
        val bestRuns = profile.member.dungeons.bestRuns
        val profilePbs = bestRuns
            .filter { run -> matchesFilter(run.floor.label, kuudra = false) }
            .groupBy { it.floor.label }
            .mapValues { (_, runs) -> runs.minOf { it.elapsedMillis } }
        val bestByFloor = bestRuns
            .filter { run -> run.teammates.any { sameUuid(it.dashless, teammate.uuid) } && matchesFilter(run.floor.label, kuudra = false) }
            .groupBy { it.floor.label }
            .mapValues { (_, runs) -> runs.minOf { it.elapsedMillis } }
            .entries.sortedBy { it.key }

        return buildList {
            add(DetailRow.Heading(tr("recorded_best", "Recorded Best Runs")))
            if (bestByFloor.isEmpty()) {
                add(DetailRow.Message(tr("no_recorded_best", "No retained best-run record with this teammate.")))
            } else {
                bestByFloor.forEach { (activity, elapsed) -> add(DetailRow.Best(activity, elapsed, profilePbs.getValue(activity))) }
            }

            add(DetailRow.Heading(tr("recent_runs", "Recent Runs")))
            personalRuns.sortedByDescending(RecentActivity::completedAt).take(MAX_RECENT_RUNS).forEach { run ->
                val role = run.participants.firstOrNull { sameUuid(it.uuid, teammate.uuid) }?.role
                add(DetailRow.RecentRun(run.activity, run.completedAt, role))
            }
        }
    }

    private fun drawDetailRow(row: DetailRow, index: Int, x: Float, y: Float, width: Float) = when (row) {
        is DetailRow.Heading -> Cards.drawHeader(x, y + DETAIL_ROW_H - Cards.HEADER_H - 1f, width, row.text)
        is DetailRow.Best -> drawAlignedRow(row.activity, bestRunValue(row), x, y, width)
        is DetailRow.RecentRun -> drawAlignedRow(row.activity, listOfNotNull(relativeTime(row.completedAt), row.role).joinToString(" · "), x, y, width)
        is DetailRow.Message -> Render.text(row.text, x, Render.centerY(y, DETAIL_ROW_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED, maxWidth = width)
    }

    private fun drawAlignedRow(label: String, value: String, x: Float, y: Float, width: Float) {
        val labelWidth = width * DETAIL_LABEL_PORTION
        val ty = Render.centerY(y, DETAIL_ROW_H, TextStyle.SMALL_MUTED)
        Render.text(label, x, ty, TextStyle.SMALL_MUTED, maxWidth = labelWidth)
        Render.textRight(value, x + width, ty, TextStyle.SMALL_PRIMARY_MEDIUM, maxWidth = width - labelWidth)
    }

    private fun sharedRuns(teammate: Teammate, activities: List<RecentActivity>): List<RecentActivity> =
        activities.filter { activity -> activity.participants.any { sameUuid(it.uuid, teammate.uuid) } }

    private fun activityBreakdown(runs: List<RecentActivity>): List<ActivitySlice> {
        val groups = runs.groupBy(RecentActivity::activity)
            .map { (activity, grouped) -> activity to grouped.size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        val colors = DonutChart.colors(groups.size)
        return groups.mapIndexed { index, (activity, count) -> ActivitySlice(activity, count, colors[index]) }
    }

    private fun drawActivityBreakdown(slices: List<ActivitySlice>, x: Float, y: Float, width: Float): Float {
        Cards.drawHeader(x, y, width, tr("shared_runs", "Shared Runs"))
        val contentY = y + Cards.HEADER_H + Theme.PADDING_SMALL
        val contentH = maxOf(DONUT_DIAMETER, slices.size * LEGEND_ROW_H)
        val centerX = x + DONUT_RADIUS
        val centerY = contentY + contentH / 2f
        val totalRuns = slices.sumOf(ActivitySlice::count)
        activityChart.segments = slices.map { slice ->
            val percent = (slice.count * 100f / totalRuns).toInt()
            DonutChart.Segment(
                slice.label,
                slice.count.toDouble(),
                slice.color,
                tr("chart_tooltip", "%s · %s · %d%%", slice.label, runCount(slice.count), percent),
            )
        }
        activityChart.progress = chartReveal.get()
        activityChart.boxWidth = DONUT_DIAMETER
        activityChart.boxHeight = DONUT_DIAMETER
        activityChart.draw(x, centerY - DONUT_RADIUS)
        val total = totalRuns.toString()
        Render.textCentered(total, centerX, Render.centerY(centerY - DONUT_RADIUS, DONUT_DIAMETER, TextStyle.SMALL_PRIMARY_MEDIUM), TextStyle.SMALL_PRIMARY_MEDIUM)

        val legendX = x + DONUT_DIAMETER + Theme.PADDING_LARGE
        val legendY = centered(contentY, contentH, slices.size * LEGEND_ROW_H)
        slices.forEachIndexed { index, slice ->
            val rowY = legendY + index * LEGEND_ROW_H
            val value = runCount(slice.count)
            val valueW = Render.textWidth(value, Theme.FONT_SMALL, Fonts.medium)
            val labelX = legendX + LEGEND_DOT * 2f + Theme.PADDING_SMALL
            val labelW = (x + width - valueW - Theme.PADDING_SMALL - labelX).coerceAtLeast(0f)
            val ty = Render.centerY(rowY, LEGEND_ROW_H, TextStyle.SMALL_MUTED)
            Render.circle(legendX + LEGEND_DOT, rowY + LEGEND_ROW_H / 2f, LEGEND_DOT, slice.color)
            Render.text(slice.label, labelX, ty, TextStyle.SMALL_MUTED, maxWidth = labelW)
            Render.text(value, x + width - valueW, ty, TextStyle.SMALL_PRIMARY_MEDIUM)
        }
        return Cards.HEADER_H + Theme.PADDING_SMALL + contentH
    }

    private fun bestRunValue(run: DetailRow.Best): String {
        val elapsed = formatDuration(run.elapsedMillis)
        if (run.elapsedMillis <= run.profilePbMillis) {
            return listOf(elapsed, tr("pb", "PB")).joinToString(" · ")
        }
        val difference = formatDuration(run.elapsedMillis - run.profilePbMillis)
        return "$elapsed · ${tr("to_pb", "+%s to PB", difference)}"
    }

    private fun runCount(count: Int): String = I18n.plural(
        count,
        tr("run_one", "run"),
        tr("run_few", "runs"),
        tr("run_many", "runs"),
    )

    private fun selected(): Teammate? = entries.firstOrNull { it.uuid == selectedUuid }

    fun renderOverlay(mouseX: Float, mouseY: Float) {
        orderDropdown.renderOverlay(mouseX, mouseY)
        activityDropdown.renderOverlay(mouseX, mouseY)
    }

    private fun relativeTime(epochMillis: Long): String {
        val seconds = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now()).seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> tr("now", "now")
            seconds < 3_600 -> tr("minutes_ago", "%dm ago", seconds / 60)
            seconds < 86_400 -> tr("hours_ago", "%dh ago", seconds / 3_600)
            else -> tr("days_ago", "%dd ago", seconds / 86_400)
        }
    }

    private fun formatDuration(millis: Long): String = "%d:%02d".format(millis / 60_000, (millis / 1_000) % 60)

    private fun sameUuid(first: String, second: String) = first.replace("-", "").equals(second.replace("-", ""), ignoreCase = true)

    private companion object {
        const val CONTROL_H = 20f
        const val ORDER_MIN_W = 92f
        const val FILTER_W = 120f
        const val LIST_PORTION = 0.39f
        const val MIN_LIST_W = 190f
        const val ROW_H = 42f
        val ROW_GAP = Theme.PADDING_SMALL
        const val DETAIL_ROW_H = 20f
        const val DETAIL_LABEL_PORTION = 0.34f
        const val MAX_RECENT_RUNS = 12
        const val DONUT_RADIUS = 28f
        const val DONUT_DIAMETER = DONUT_RADIUS * 2f
        const val LEGEND_DOT = 3f
        const val LEGEND_ROW_H = 15f
        const val ALL_ACTIVITY = "all"
        const val DUNGEON_ACTIVITY = "dungeon"
        const val MASTER_ACTIVITY = "master"
        const val KUUDRA_ACTIVITY = "kuudra"
        val IGN = Regex("[A-Za-z0-9_]{1,16}")
    }
}
