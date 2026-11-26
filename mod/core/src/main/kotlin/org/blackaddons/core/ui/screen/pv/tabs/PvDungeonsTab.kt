package org.blackaddons.core.ui.screen.pv.tabs

import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.profile.DungeonClassStat
import org.blackaddons.api.skyblock.profile.FloorStats
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.formatGrouped
import org.blackaddons.core.ui.screen.pv.PvFormat
import java.awt.Color

private val tr = I18n.scope("blackaddons.pv.dungeons_detail")

internal class PvDungeonsTab {

    val widgets = WidgetHost()
    private val scroll = widgets.add(Scroll())
    private val reveal = Animation(durationMs = Theme.ANIM_NORMAL.toLong())

    fun reset() {
        scroll.reset()
        reveal.set(0f)
    }

    fun hide() = Unit

    fun render(x: Float, y: Float, width: Float, height: Float, member: ProfileMember) {
        reveal.animateTo(1f)
        Cards.scrolled(scroll, x, y, width, height) { w -> content(w, member) }
    }

    private fun content(width: Float, member: ProfileMember): LayoutNode {
        val floors = member.dungeons.floors
        val totalRuns = floors.sumOf(FloorStats::runs)
        return column(spacing = Cards.GAP, width = width) {
            add(overviewCard(width, member, totalRuns))
            add(classesCard(width, member.dungeons.classes))
            sectionHeader(width, tr("floors", "Dungeon Floors"), tr("total_runs", "Total Runs") + ": " + totalRuns.formatGrouped())
            add(floorCards(width, floors))
            if (floors.isNotEmpty()) add(completionCard(width, floors))
        }
    }

    private fun overviewCard(width: Float, member: ProfileMember, totalRuns: Int): LayoutNode =
        Cards.card(width, tr("overview", "Dungeon Overview")) { innerW ->
            val secretsPerRun = if (totalRuns > 0) (member.dungeons.secretsFound.toDouble() / totalRuns).formatDecimals() else "0.00"
            stats(
                innerW,
                listOf(
                    Stat(tr("cata_level", "Catacombs Level"), (member.dungeons.catacombsLevel + member.dungeons.catacombsProgress).formatDecimals()),
                    Stat(tr("blood_mobs", "Blood Mobs"), member.dungeons.bloodMobKills.formatGrouped()),
                    Stat(tr("total_runs", "Total Runs"), totalRuns.formatGrouped()),
                    Stat(tr("secrets", "Secrets"), member.dungeons.secretsFound.formatGrouped()),
                    Stat(tr("secrets_per_run", "Secrets / Run"), secretsPerRun),
                    Stat(tr("magical_power", "Magical Power"), member.magicalPower.formatGrouped()),
                ),
                columns = OVERVIEW_COLUMNS,
            )
        }

    private fun classesCard(width: Float, classes: List<DungeonClassStat>): LayoutNode {
        val average = classes.map { it.level + it.progress }.average()
        val names = classes.map { PvFormat.dungeonClassName(it.dungeonClass.displayName) }
        val labelW = names.maxOfOrNull { Render.textWidth(it, Theme.FONT_SMALL, Fonts.medium) } ?: 0f
        return Cards.card(
            width,
            tr("class_levels", "Class Levels"),
            trailing = tr("class_average", "Class Average") + ": " + average.formatDecimals(),
        ) { innerW ->
            classes.sortedByDescending { it.level + it.progress }.forEach { stat ->
                val color = stat.dungeonClass.color
                progressRow(
                    innerW,
                    PvFormat.dungeonClassName(stat.dungeonClass.displayName),
                    (stat.level + stat.progress).formatDecimals(),
                    classProgress(stat) * reveal.get(),
                    color,
                    labelW,
                    dot = color,
                )
            }
        }
    }

    /** Normal floors on the left, Master Mode on the right, one row per floor number. */
    private fun floorCards(width: Float, floors: List<FloorStats>): LayoutNode {
        val normal = floors.filterNot { it.floor.isMasterMode }
        val master = floors.filter { it.floor.isMasterMode }
        if (normal.isEmpty() && master.isEmpty()) {
            return Cards.card(width) { innerW -> note(innerW, tr("no_floors", "No completed Dungeon floors.")) }
        }
        val cardW = (width - Cards.GAP) / 2f
        return column(spacing = Cards.GAP, width = width) {
            repeat(maxOf(normal.size, master.size)) { index ->
                row(spacing = Cards.GAP, width = width) {
                    add(normal.getOrNull(index)?.let { floorCard(cardW, it) } ?: Spacer(cardW))
                    master.getOrNull(index)?.let { add(floorCard(cardW, it)) }
                }
            }
        }
    }

    private fun floorCard(width: Float, floor: FloorStats): LayoutNode {
        val color = floorColor(floor.floor)
        return Cards.card(width) { innerW ->
            val titleH = Render.textHeight(Theme.FONT_BODY, Fonts.medium)
            cell(innerW, titleH) { cx, cy ->
                Render.text(floor.floor.label, cx, cy, Theme.FONT_BODY, color, Fonts.medium)
                val runs = floor.runs.formatGrouped() + " " + tr("runs", "runs")
                Render.textRight(runs, cx + innerW, Render.centerY(cy, titleH, TextStyle.SMALL_SECONDARY_MEDIUM), TextStyle.SMALL_SECONDARY_MEDIUM)
            }
            stats(
                innerW,
                listOf(
                    Stat(tr("best_score", "Best Score"), floor.bestScore.toString()),
                    Stat(tr("fastest_s", "Fastest S"), formatDuration(floor.fastestS)),
                    Stat(tr("fastest_s_plus", "Fastest S+"), formatDuration(floor.fastestSPlus), color),
                ),
            )
        }
    }

    private fun completionCard(width: Float, floors: List<FloorStats>): LayoutNode =
        Cards.card(width, tr("floor_completions", "Floor Completions")) { innerW ->
            val maxRuns = floors.maxOf(FloorStats::runs).coerceAtLeast(1)
            val labelH = Render.textHeight(Theme.FONT_SMALL, Fonts.regular)
            cell(innerW, COMPLETION_BAR_H + Theme.PADDING_SMALL + labelH) { cx, cy ->
                val barW = (columnWidth(innerW, floors.size, Theme.PADDING_SMALL)).coerceAtLeast(0f)
                floors.forEachIndexed { index, floor ->
                    val barX = cx + index * (barW + Theme.PADDING_SMALL)
                    val barH = COMPLETION_BAR_H * floor.runs.toFloat() / maxRuns * reveal.get()
                    Render.rect(barX, cy + COMPLETION_BAR_H - barH, barW, barH, floorColor(floor.floor), Theme.RADIUS_SMALL)
                    val label = if (floor.floor == DungeonFloor.ENTRANCE) "E" else floor.floor.label
                    Render.textCentered(label, barX + barW / 2f, cy + COMPLETION_BAR_H + Theme.PADDING_SMALL, TextStyle.SMALL_MUTED)
                    if (hovered(barX, cy, barW, COMPLETION_BAR_H)) {
                        Tooltip.show("${floor.floor.label}: ${floor.runs.formatGrouped()} ${tr("runs", "runs")}")
                    }
                }
            }
        }

    private fun classProgress(dungeonClass: DungeonClassStat): Double = ((dungeonClass.level + dungeonClass.progress) / CLASS_LEVEL_CAP).coerceIn(0.0, 1.0)

    private fun formatDuration(millis: Long?): String {
        millis ?: return "--"
        return "%d:%02d".format(millis / 60_000, (millis / 1_000) % 60)
    }

    private fun floorColor(floor: DungeonFloor): Color = if (floor.isMasterMode) MASTER_COLOR else Theme.ACCENT

    private companion object {
        const val OVERVIEW_COLUMNS = 3
        const val CLASS_LEVEL_CAP = 50.0
        const val COMPLETION_BAR_H = 66f

        val MASTER_COLOR = Color(211, 84, 0)
    }
}
