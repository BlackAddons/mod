package org.blackaddons.core.ui.screen.pv.tabs
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.skyblock.profile.SkillStat
import org.blackaddons.api.ui.layout.Cards
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.Stat
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.grid
import org.blackaddons.api.ui.layout.sectionHeader
import org.blackaddons.api.ui.layout.stats
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.formatPercent
import org.blackaddons.core.ui.screen.pv.PvFormat
import org.blackaddons.core.ui.screen.pv.parts.PvIcons
import org.blackaddons.core.ui.screen.pv.parts.PvProgressCard
import java.awt.Color
import java.util.Locale
import kotlin.math.roundToInt

private val tr = I18n.scope("blackaddons.pv")

internal class PvSkillsTab {

    val widgets = WidgetHost()
    private val scroll = widgets.add(Scroll())
    private val cards = mutableMapOf<String, PvProgressCard>()
    private var selectedName: String? = null

    fun reset() {
        selectedName = null
        scroll.reset()
        hide()
    }

    fun hide() = cards.values.forEach(PvProgressCard::clear)

    fun render(x: Float, y: Float, width: Float, height: Float, member: ProfileMember) {
        val selected = member.skills.firstOrNull { it.name == selectedName } ?: member.skills.firstOrNull() ?: return
        selectedName = selected.name
        Cards.scrolled(scroll, x, y, width, height) { w -> content(w, height - Cards.INSET * 2f, selected, member) }
    }

    private fun content(width: Float, height: Float, selected: SkillStat, member: ProfileMember): LayoutNode {
        val summary = member.skills.summary()
        val gap = Cards.GAP

        val top = column(width = width) {
            sectionHeader(width, tr("skills", "Skills"), tr("skills_detail.average", "Skill Average: %s", summary.coreAverage.formatDecimals()))
        }
        val summaryCard = summaryCard(width, summary)

        // The details card takes any rest, so the summary ends on the panel's bottom edge. Too short a panel scrolls.
        val rows = (member.skills.size + COLUMNS - 1) / COLUMNS
        val gridH = rows * CARD_H + (rows - 1) * gap
        val detailsH = height - top.height - gridH - summaryCard.height - gap * 3f

        return column(spacing = gap, width = width) {
            add(top)
            grid(width, member.skills.size, COLUMNS, CARD_H, gap) { index, cx, cy, cellW ->
                val skill = member.skills[index]
                val card = cards.getOrPut(skill.name) { widgets.add(PvProgressCard { selectedName = skill.name }) }
                card.set(skill.cardContent(), skill.name == selectedName)
                card.render(cx, cy, cellW, CARD_H)
            }
            add(detailsCard(width, selected, minHeight = detailsH))
            add(summaryCard)
        }
    }

    private fun detailsCard(width: Float, skill: SkillStat, minHeight: Float): LayoutNode {
        val progress = if (skill.level >= skill.cap) {
            tr("skills_detail.cap_reached", "Current cap reached")
        } else {
            tr("skills_detail.to_next", "%d%% to the next level", (skill.progress * 100).roundToInt())
        }
        val valueColor = if (skill.maxed) MAXED else Theme.ACCENT
        val stats = listOf(
            Stat(tr("level", "Level"), "${PvFormat.skillLevel(skill)} / ${skill.cap}", valueColor),
            Stat(tr("skills_detail.xp", "XP"), "%,.0f".format(Locale.ROOT, skill.xp), valueColor),
            Stat(tr("skills_detail.progress", "Progress"), skill.progress.formatPercent(), valueColor),
        )
        val text = column(spacing = Theme.PADDING_SMALL) {
            text(skill.name, TextStyle.TITLE_PRIMARY_MEDIUM)
            text(progress, TextStyle.SMALL_SECONDARY)
        }
        val iconSize = text.height + Theme.PADDING_SMALL + metricHeight()
        return Cards.card(width, minHeight = minHeight, centered = true) { innerW ->
            row(spacing = Theme.PADDING, width = innerW) {
                cell(iconSize, iconSize) { ix, iy -> PvProgressCard.drawIcon(PvIcons.skill(skill.name.lowercase()), ix, iy, iconSize) }
                column(spacing = Theme.PADDING_SMALL, width = innerW - iconSize - Theme.PADDING) {
                    add(text)
                    stats(innerW - iconSize - Theme.PADDING, stats)
                }
            }
        }
    }

    private fun summaryCard(width: Float, summary: SkillSummary): LayoutNode =
        Cards.card(width, tr("skills_detail.progression", "Skill Progression")) { innerW ->
            stats(
                innerW,
                listOf(
                    Stat(tr("skills_detail.total_xp", "Total Skill XP"), PvFormat.compact(summary.totalXp)),
                    Stat(tr("skills_detail.maxed", "Maxed Skills"), "${summary.maxedSkills} / ${summary.skillCount}"),
                    Stat(tr("skills_detail.core_average", "Core Average"), summary.coreAverage.formatDecimals()),
                    Stat(tr("skills_detail.all_average", "All Skills Average"), summary.allAverage.formatDecimals()),
                ),
            )
        }

    private fun metricHeight(): Float =
        Render.textHeight(Theme.FONT_SMALL, Fonts.bold) + Theme.PADDING_SMALL + Render.textHeight(Theme.FONT_BODY, Fonts.medium)

    private fun SkillStat.cardContent() = PvProgressCard.Content(
        label = name,
        value = tr("skills_detail.level_short", "Lv %s / %s", PvFormat.skillLevel(this), cap),
        progress = progress,
        icon = PvIcons.skill(name.lowercase()),
        detail = tr("skills_detail.tooltip", "%s\nLevel %s / %s\nXP: %,.0f", name, PvFormat.skillLevel(this), cap, xp),
        accent = if (maxed) MAXED else Theme.ACCENT,
    )

    private fun List<SkillStat>.summary() = SkillSummary(
        totalXp = sumOf(SkillStat::xp),
        maxedSkills = count(SkillStat::maxed),
        skillCount = size,
        coreAverage = filter(SkillStat::countsTowardAverage).map(PvFormat::skillLevelValue).average(),
        allAverage = map(PvFormat::skillLevelValue).average(),
    )

    private data class SkillSummary(
        val totalXp: Double,
        val maxedSkills: Int,
        val skillCount: Int,
        val coreAverage: Double,
        val allAverage: Double,
    )

    private companion object {
        const val COLUMNS = 3
        const val CARD_H = 42f
        val MAXED = Color(255, 215, 90)
    }
}
