package org.blackaddons.core.ui.screen.pv.tabs

import org.blackaddons.api.skyblock.profile.BestiaryMobStat
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.skyblock.profile.SlayerStat
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatGrouped
import org.blackaddons.core.ui.screen.pv.PvFormat
import org.blackaddons.core.ui.screen.pv.parts.PvIcons
import org.blackaddons.core.ui.screen.pv.parts.PvProgressCard
import kotlin.math.roundToInt

private val tr = I18n.scope("blackaddons.pv")

/** One Bestiary creature family, ready to show, after the numeric API variants are combined. */
internal data class PvBestiaryGroup(val id: String, val kills: Long, val deaths: Long)

internal class PvCombatTab {

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
        if (!member.hasCombatData()) {
            hide()
            Cards.empty(
                width, height,
                tr("combat_detail.empty_title", "No Combat activity"),
                tr("combat_detail.empty_detail", "This profile has no recorded Slayer or Bestiary activity."),
            ).draw(x, y)
            return
        }
        Cards.scrolled(scroll, x, y, width, height) { w -> content(w, member) }
    }

    private fun content(width: Float, member: ProfileMember): LayoutNode {
        val selected = member.slayers.firstOrNull { it.name == selectedName }
            ?: member.slayers.maxBy(SlayerStat::xp).also { selectedName = it.name }
        val combat = member.skills.firstOrNull { it.name == "Combat" }
        val level = combat?.let { "${tr("level", "Level")} ${PvFormat.skillLevel(it)}" }
        val totalXp = "${tr("combat_detail.total_slayer_xp", "Total Slayer XP")}: ${PvFormat.compact(member.slayers.sumOf(SlayerStat::xp))}"
        return column(spacing = Cards.GAP, width = width) {
            sectionHeader(width, tr("slayers", "Slayers"), listOfNotNull(level, totalXp).joinToString(" · "))
            grid(width, member.slayers.size, COLUMNS, CARD_H) { index, cx, cy, cellW ->
                val slayer = member.slayers[index]
                val card = cards.getOrPut(slayer.name) { widgets.add(PvProgressCard { selectedName = slayer.name }) }
                card.set(slayer.cardContent(), slayer.name == selectedName)
                card.render(cx, cy, cellW, CARD_H)
            }
            add(detailsCard(width, selected))
            add(bestiaryCard(width, member.bestiary))
        }
    }

    private fun detailsCard(width: Float, slayer: SlayerStat): LayoutNode {
        val progress = if (slayer.level >= slayer.cap) {
            tr("combat_detail.max_level", "Maximum level reached")
        } else {
            tr("combat_detail.to_next", "%d%% to next level").format((slayer.progress * 100).roundToInt())
        }
        val tiers = visibleTiers(slayer.kills)
        return Cards.card(width) { innerW ->
            row(spacing = Theme.PADDING, width = innerW) {
                cell(ICON_SIZE, ICON_SIZE) { ix, iy -> PvProgressCard.drawIcon(PvIcons.slayer(slayer.name.lowercase()), ix, iy, ICON_SIZE) }
                val textW = innerW - ICON_SIZE - Theme.PADDING
                column(spacing = Theme.PADDING_SMALL, width = textW) {
                    row(width = textW) {
                        column(spacing = Theme.PADDING_SMALL) {
                            text(PvFormat.slayerName(slayer.name), TextStyle.TITLE_PRIMARY_MEDIUM)
                            text(tr("level", "Level") + " ${slayer.level} / ${slayer.cap}", Theme.FONT_SMALL, Theme.ACCENT, Fonts.medium)
                        }
                        flexSpacer()
                        val metricW = textW * METRICS_PORTION / 2f
                        stat(metricW, Stat(tr("combat_detail.slayer_xp", "Slayer XP"), PvFormat.compact(slayer.xp)))
                        spacer(width = Theme.PADDING)
                        stat(metricW, Stat(tr("combat_detail.total_kills", "Total Kills"), slayer.kills.sum().formatGrouped()))
                    }
                    cell(textW, Cards.ROW_H) { bx, by -> Cards.drawBar(bx, by, textW, Cards.ROW_H, slayer.progress, Theme.ACCENT) }
                    text(progress, TextStyle.SMALL_SECONDARY)
                }
            }
            sectionHeader(innerW, tr("combat_detail.tier_kills", "Tier Kills"))
            if (tiers.isEmpty()) {
                note(innerW, tr("combat_detail.no_kills", "No recorded boss kills."))
            } else {
                stats(
                    innerW,
                    tiers.mapIndexed { index, kills -> Stat(tr("tier", "Tier %s").format(PvFormat.slayerTier(index)), kills.formatGrouped()) },
                )
            }
        }
    }

    private fun bestiaryCard(width: Float, mobs: List<BestiaryMobStat>): LayoutNode {
        val groups = bestiaryGroups(mobs)
        return Cards.card(width, tr("combat_detail.bestiary", "Bestiary")) { innerW ->
            stats(
                innerW,
                listOf(
                    Stat(tr("combat_detail.bestiary_kills", "Bestiary Kills"), PvFormat.compact(groups.sumOf(PvBestiaryGroup::kills).toDouble())),
                    Stat(tr("combat_detail.bestiary_deaths", "Deaths"), PvFormat.compact(groups.sumOf(PvBestiaryGroup::deaths).toDouble())),
                ),
            )
            val listW = (innerW - Theme.PADDING_LARGE) / 2f
            row(spacing = Theme.PADDING_LARGE, width = innerW) {
                add(bestiaryList(listW, tr("combat_detail.top_kills", "Top Kills"), groups, PvBestiaryGroup::kills))
                add(bestiaryList(listW, tr("combat_detail.top_deaths", "Top Deaths"), groups, PvBestiaryGroup::deaths))
            }
        }
    }

    private fun bestiaryList(width: Float, title: String, groups: List<PvBestiaryGroup>, value: (PvBestiaryGroup) -> Long): LayoutNode {
        val top = groups.filter { value(it) > 0L }.sortedWith(compareByDescending(value).thenBy(PvBestiaryGroup::id)).take(TOP_BESTIARY)
        return column(spacing = 0f, width = width) {
            sectionHeader(width, title)
            if (top.isEmpty()) note(width, tr("combat_detail.no_bestiary", "No recorded Bestiary activity."))
            top.forEach { group -> valueRow(width, PvFormat.prettyCategory(group.id), PvFormat.compact(value(group).toDouble())) }
        }
    }

    private fun SlayerStat.cardContent() = PvProgressCard.Content(
        label = PvFormat.slayerName(name),
        value = tr("skills_detail.level_short", "Lv %s / %s", level, cap),
        progress = progress,
        icon = PvIcons.slayer(name.lowercase()),
        detail = buildString {
            append(PvFormat.slayerName(name)).append('\n')
            append(tr("level", "Level")).append(": ").append(level).append(" / ").append(cap)
            append('\n').append(tr("combat_detail.slayer_xp", "Slayer XP")).append(": ").append(PvFormat.exact(xp))
            append('\n').append(tr("combat_detail.total_kills", "Total Kills")).append(": ")
                .append(kills.sum().formatGrouped())
        },
    )

    private fun ProfileMember.hasCombatData() = slayers.any { it.hasHistory() } || bestiary.isNotEmpty()
    private fun SlayerStat.hasHistory() = xp > 0.0 || kills.any { it > 0 }

    companion object {
        /** Drops untouched top tiers, so no zeros show. */
        internal fun visibleTiers(kills: List<Int>): List<Int> = kills.take(kills.indexOfLast { it > 0 } + 1)

        /** Merges IDs that only differ by a numeric variant. */
        internal fun bestiaryGroups(mobs: List<BestiaryMobStat>): List<PvBestiaryGroup> = mobs
            .groupBy { it.id.withoutNumericVariant() }
            .map { (id, variants) -> PvBestiaryGroup(id, variants.sumOf(BestiaryMobStat::kills), variants.sumOf(BestiaryMobStat::deaths)) }
            .sortedBy(PvBestiaryGroup::id)

        private fun String.withoutNumericVariant(): String {
            val suffix = substringAfterLast('_', missingDelimiterValue = "")
            return if (suffix.isNotEmpty() && suffix.all(Char::isDigit)) substringBeforeLast('_') else this
        }

        private const val COLUMNS = 3
        private const val CARD_H = 42f
        private const val ICON_SIZE = 40f
        private const val METRICS_PORTION = 0.38f
        private const val TOP_BESTIARY = 3
    }
}
