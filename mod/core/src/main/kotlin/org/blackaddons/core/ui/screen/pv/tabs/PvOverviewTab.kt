package org.blackaddons.core.ui.screen.pv.tabs
import org.blackaddons.api.skyblock.profile.PlayerProfiles
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.ProgressBar
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.formatPercent
import org.blackaddons.core.profile.SkinTextures
import org.blackaddons.core.ui.screen.pv.PvFormat
import org.blackaddons.core.ui.screen.pv.SkyblockLevelTier
import org.blackaddons.core.ui.screen.pv.parts.ChipIcon
import org.blackaddons.core.ui.screen.pv.parts.PvAvatarColumn
import org.blackaddons.core.ui.screen.pv.parts.PvIcons
import org.blackaddons.core.ui.screen.pv.tabs.PvOverviewTab.Companion.CHIP_PAD
import java.awt.Color
import java.util.Locale
import kotlin.math.floor

private val tr = I18n.scope("blackaddons.pv")

/** Overview: avatar, economy + profile, skills columns. Layout only. [PvAvatarColumn] owns state and input. */
internal class PvOverviewTab {

    private val avatar = PvAvatarColumn()
    val widgets = WidgetHost().apply { add(avatar) }

    /** One shared reveal for all bars when a profile loads, not a tween per chip. */
    private val reveal = Animation(durationMs = Theme.ANIM_NORMAL.toLong())

    private fun revealed(progress: Double): Double = progress * reveal.get()

    fun reset() {
        avatar.reset()
        reveal.set(0f)
    }

    fun hide() = avatar.hide()

    fun render(
        x: Float, top: Float, width: Float, height: Float,
        profiles: PlayerProfiles, textures: SkinTextures?, member: ProfileMember,
        mouseX: Float, mouseY: Float, deltaTicks: Float,
    ) {
        reveal.animateTo(1f)
        val gap = Theme.PADDING_LARGE
        // On a wide screen the avatar and the info rows gain nothing from more width, so the chips get it.
        val avatarW = minOf(width * AVATAR_SHARE, height * AVATAR_ASPECT)
        val infoW = minOf(width * INFO_SHARE, height * INFO_ASPECT)
        val rightW = width - avatarW - infoW - gap * 2f

        row(spacing = gap) {
            add(avatar.node(profiles, textures, member.skyblockLevel, avatarW, height, Cards.ROW_H, mouseX, mouseY, deltaTicks))
            add(middleFit.node(member, infoW, height) { scale -> middleColumn(member, infoW, scale) })
            add(rightFit.node(member, rightW, height) { scale -> rightColumn(member, rightW, scale) })
        }.draw(x, top)
    }

    /**
     * The largest chip scale at which a column still fits the tab's height, so chips grow into the room a big screen
     * leaves instead of sitting small in it. Found once per size and profile, not every frame.
     */
    private class ChipFit {
        private var key: Triple<ProfileMember, Float, Float>? = null
        private var scale = 1f

        fun node(member: ProfileMember, w: Float, h: Float, build: (Float) -> LayoutNode): LayoutNode {
            val now = Triple(member, w, h)
            if (key != now) {
                key = now
                scale = generateSequence(MAX_CHIP_SCALE) { it - CHIP_SCALE_STEP }.takeWhile { it > 1f }
                    .firstOrNull { build(it).height <= h } ?: 1f
            }
            return build(scale)
        }
    }

    private val middleFit = ChipFit()
    private val rightFit = ChipFit()

    private fun middleColumn(member: ProfileMember, w: Float, scale: Float): LayoutNode =
        column(spacing = Theme.PADDING, width = w) {
            add(economyCard(member, w))
            add(profileCard(member, w))
            if (member.slayers.any { it.level > 0 }) add(slayersCard(member, w, scale))
        }

    /** 3x2 slayer chips. Here and not under skills, because there the grids ran off the panel. */
    private fun slayersCard(member: ProfileMember, w: Float, scale: Float): LayoutNode {
        val innerW = w - Theme.PADDING * 2f
        return column(spacing = Theme.PADDING_SMALL, width = innerW) {
            sectionHeader(innerW, tr("slayers", "Slayers"))
            add(
                statGrid(
                    member.slayers.map {
                        StatChip(
                            PvIcons.slayer(it.name.lowercase()), it.level, false,
                            tooltip = PvFormat.slayerKillsTooltip(it.kills, it.xp),
                        )
                    },
                    innerW, scale,
                    chipW = SMALL_CHIP_W, chipH = SMALL_CHIP_H, perRow = SLAYERS_PER_ROW,
                ),
            )
        }.padded(Theme.PADDING).background(Theme.SURFACE, Theme.RADIUS)
    }

    private fun economyCard(member: ProfileMember, w: Float): LayoutNode {
        val innerW = w - Theme.PADDING * 2f
        val nw = member.networth
        val nwLabel = tr("networth", "Networth")
        val valued = if (nw != null) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED
        val tip = nw?.let { PvFormat.networthTooltip(it, nwLabel) }
        return column(spacing = Theme.PADDING_SMALL, width = innerW) {
            sectionHeader(innerW, tr("economy", "Economy"))
            valueRow(innerW, nwLabel, if (nw != null) PvFormat.compact(nw.total) else "—", valued, tip, hint = tip != null)
            valueRow(innerW, tr("purse_label", "Purse"), "%,.0f".format(Locale.ROOT, member.purse), Theme.TEXT_PRIMARY, null)
            valueRow(innerW, tr("bank", "Bank"), if (nw != null) PvFormat.compact(nw.bank) else "—", valued, null)
        }.padded(Theme.PADDING).background(Theme.SURFACE, Theme.RADIUS)
    }

    private fun profileCard(member: ProfileMember, w: Float): LayoutNode {
        val innerW = w - Theme.PADDING * 2f
        val tier = SkyblockLevelTier.color(member.skyblockLevel)
        val joined = member.firstJoin
        return column(spacing = Theme.PADDING_SMALL, width = innerW) {
            sectionHeader(innerW, tr("profile", "Profile"))
            valueRow(innerW, tr("sb_level", "Skyblock Level"), member.skyblockLevel.toString(), tier, null)
            cell(innerW, Cards.ROW_H) { x, y ->
                val pct = member.skyblockProgress.formatPercent()
                val pw = Render.textWidth(pct, Theme.FONT_SMALL, Fonts.regular)
                val barW = innerW - pw - Theme.PADDING
                ProgressBar.draw(x, y + Cards.ROW_H / 2f - 2f, barW, 4f, revealed(member.skyblockProgress), fill = tier)
                Render.text(pct, x + innerW - pw, Render.centerY(y, Cards.ROW_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED)
            }
            valueRow(innerW, tr("skill_avg", "Skill Average"), member.skillAverage.formatDecimals(), Theme.TEXT_PRIMARY, null)
            valueRow(innerW, tr("magical_power", "Magical Power"), member.magicalPower.toString(), Theme.TEXT_PRIMARY, null)
            valueRow(innerW, tr("fairy_souls", "Fairy Souls"), member.fairySouls.toString(), Theme.TEXT_PRIMARY, null)
            valueRow(
                innerW, tr("profile_age", "Profile Age"), PvFormat.profileAge(joined) ?: "—",
                if (joined > 0) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED, PvFormat.joinTooltip(joined),
            )
            valueRow(
                innerW, tr("cookie_buff", "Cookie Buff"),
                if (member.cookieBuff) tr("active", "Active") else "—",
                if (member.cookieBuff) Theme.SUCCESS else Theme.TEXT_MUTED, null,
            )
        }.padded(Theme.PADDING).background(Theme.SURFACE, Theme.RADIUS)
    }

    private fun rightColumn(member: ProfileMember, w: Float, scale: Float): LayoutNode = column(spacing = Theme.PADDING, width = w) {
        sectionHeader(w, tr("skills", "Skills"))
        add(statGrid(member.skills.map { StatChip(PvIcons.skill(it.name.lowercase()), it.level, it.maxed, it.progress) }, w, scale))
        add(catacombsCard(member, w))
        if (member.dungeons.classes.any { it.level > 0 }) {
            sectionHeader(w, tr("classes", "Dungeon Classes"))
            add(statGrid(member.dungeons.classes.map {
                StatChip(
                    PvIcons.dungeonClass(it.dungeonClass.displayName.lowercase()), it.level, it.level >= CLASS_CAP, it.progress,
                    highlight = it.selected, badge = it.dungeonClass.displayName.take(1).uppercase(),
                )
            }, w, scale, chipW = CLASS_CHIP_W, perRow = classesPerRow(member.dungeons.classes.size, w)))
        }
    }

    /** Own card, because next to the label the bar only had a few pixels. */
    private fun catacombsCard(member: ProfileMember, w: Float): LayoutNode {
        val innerW = w - Theme.PADDING * 2f
        return column(spacing = Theme.PADDING_SMALL, width = innerW) {
            sectionHeader(innerW, tr("catacombs", "Catacombs"))
            cell(innerW, CATA_ROW_H) { x, y ->
                val head = CATA_ROW_H - CATA_HEAD_INSET * 2f
                Render.playerHead(CATACOMBS_HEAD_UUID, "Catacombs", CATACOMBS_HEAD, x, y + CATA_HEAD_INSET, head, head)
                Render.text(
                    tr("level", "Level"), x + head + 8f,
                    Render.centerY(y, CATA_ROW_H, TextStyle.SMALL_MUTED),
                    TextStyle.SMALL_MUTED,
                )
                val v = member.dungeons.catacombsLevel.toString()
                Render.text(
                    v, x + innerW - Render.textWidth(v, Theme.FONT_BODY, Fonts.medium),
                    Render.centerY(y, CATA_ROW_H, Theme.FONT_BODY, Fonts.medium),
                    Theme.FONT_BODY, Theme.SUCCESS, Fonts.medium,
                )
            }
            cell(innerW, Cards.ROW_H) { x, y ->
                val pct = member.dungeons.catacombsProgress.formatPercent()
                val pw = Render.textWidth(pct, Theme.FONT_SMALL, Fonts.regular)
                ProgressBar.draw(x, y + Cards.ROW_H / 2f - 2f, innerW - pw - Theme.PADDING, 4f, revealed(member.dungeons.catacombsProgress), fill = Theme.SUCCESS)
                Render.text(
                    pct, x + innerW - pw, Render.centerY(y, Cards.ROW_H, TextStyle.SMALL_MUTED),
                    TextStyle.SMALL_MUTED,
                )
            }
        }.padded(Theme.PADDING).background(Theme.SURFACE, Theme.RADIUS)
    }

    /** [progress] (0..1) draws a bottom bar, null for slayers. [highlight] marks the current class. */
    private class StatChip(
        val icon: ChipIcon?,
        val level: Int,
        val maxed: Boolean,
        val progress: Double? = null,
        val highlight: Boolean = false,
        /** Letter next to icons that look the same. */
        val badge: String? = null,
        val tooltip: String? = null,
    )

    // All five in one row when each still gets most of its width. On a narrow screen they wrap instead of shrinking.
    private fun classesPerRow(count: Int, w: Float): Int? {
        val width = (w - (count - 1) * Theme.PADDING_SMALL) / count
        return count.takeIf { width >= CLASS_CHIP_W * MIN_ROW_FIT }
    }

    /**
     * Chips at [scale] wrapped into rows that fill [w]: as many as fit at their scaled minimum width, or [perRow],
     * stretched to the full row.
     */
    private fun statGrid(
        items: List<StatChip>, w: Float, scale: Float,
        chipW: Float = CHIP_W, chipH: Float = CHIP_H, perRow: Int? = null,
    ): LayoutNode {
        val g = Theme.PADDING_SMALL
        val columns = perRow ?: maxOf(1, ((w + g) / (chipW * scale + g)).toInt())
        val width = (w - (columns - 1) * g) / columns
        // A fixed column count can leave a chip narrower than its scale needs, the content then shrinks to fit.
        val fit = minOf(scale, width / chipW)
        val height = chipH * fit
        return column(spacing = g, width = w) {
            items.chunked(columns).forEach { rowItems ->
                cell(w, height) { x, y ->
                    rowItems.forEachIndexed { i, item ->
                        chipNode(item, width, height, fit).draw(x + i * (width + g), y)
                    }
                }
            }
        }
    }

    private fun chipNode(item: StatChip, chipW: Float, chipH: Float, scale: Float): LayoutNode = Cell(chipW, chipH) { x, y ->
        Render.rect(x, y, chipW, chipH, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
        // Current class: accent border and text. A fill alone is hard to see.
        if (item.highlight) {
            Render.rect(x, y, chipW, chipH, Theme.ACCENT_SOFT, Theme.RADIUS_SMALL)
            Render.hollowRect(x, y, chipW, chipH, 1f, Theme.ACCENT, Theme.RADIUS_SMALL)
        }
        val pad = CHIP_PAD * scale
        val barRoom = CHIP_BAR_ROOM * scale
        val font = Theme.FONT_SMALL * scale
        val contentH = if (item.progress != null) chipH - barRoom else chipH
        // Whole pixels: items come from an atlas, so fractional sizes resample and mess up the edges.
        val iconSize = floor(contentH - CHIP_ICON_INSET * scale * 2f)
        val iy = floor(centered(y, contentH, iconSize))
        val ix = floor(x + pad)
        when (val ic = item.icon) {
            is ChipIcon.Item -> Render.itemSpec(ic.spec, ix, iy, iconSize, iconSize)
            is ChipIcon.Head -> Render.playerHead(ic.uuid, ic.name, ic.texture, ix, iy, iconSize, iconSize)
            null -> {}
        }
        // Without Hypixel's pack, two classes can share a sword, so the letter names the chip.
        item.badge?.let {
            Render.text(
                it, ix + iconSize + BADGE_GAP * scale, Render.centerY(y, contentH, font, Fonts.bold),
                font, if (item.highlight) Theme.ACCENT else Theme.TEXT_MUTED, Fonts.bold,
            )
        }
        val lvl = when {
            item.progress == null || item.maxed -> item.level.toString()
            else -> (item.level + item.progress).formatDecimals()
        }
        val color = when {
            item.maxed -> MAXED
            item.highlight -> Theme.ACCENT
            else -> Theme.TEXT_PRIMARY
        }
        Render.text(lvl, x + chipW - Render.textWidth(lvl, font, Fonts.medium) - pad, Render.centerY(y, contentH, font, Fonts.medium), font, color, Fonts.medium)
        item.progress?.let {
            ProgressBar.draw(x + pad, y + chipH - barRoom, chipW - pad * 2f, CHIP_BAR_H * scale, revealed(if (item.maxed) 1.0 else it), fill = if (item.maxed) MAXED else Theme.ACCENT)
        }
        // Ambient pointer, so nested chips don't need a mouse passed down.
        item.tooltip?.let { tip ->
            if (hovered(x, y, chipW, chipH)) Tooltip.show(tip)
        }
    }

    private companion object {
        // Wide enough for a level with two decimals. At 16px an icon is a smudge.
        const val CHIP_W = 76f
        const val CHIP_H = 30f
        const val CHIP_PAD = 5f

        /** Tighter than [CHIP_PAD], because the icon has to be as big as the chip allows. */
        const val CHIP_ICON_INSET = 2f

        const val SMALL_CHIP_W = 60f
        const val SMALL_CHIP_H = 22f

        const val CHIP_BAR_ROOM = 6f
        const val CHIP_BAR_H = 3f

        // A class chip also carries its letter, so it needs more room before the level runs into it.
        const val CLASS_CHIP_W = 92f
        const val MIN_ROW_FIT = 0.85f

        /** How far chips can grow into spare room. Past this they look like buttons, not stats. */
        const val MAX_CHIP_SCALE = 1.5f
        const val CHIP_SCALE_STEP = 0.05f

        // Column shares of the tab width, and the most each can take per unit of height on a wide screen.
        const val AVATAR_SHARE = 0.30f
        const val AVATAR_ASPECT = 0.6f
        const val INFO_SHARE = 0.27f
        const val INFO_ASPECT = 0.62f

        const val BADGE_GAP = 4f

        /** Six slayers look right as a 3x2 block. Wrapping on width leaves an uneven last row. */
        const val SLAYERS_PER_ROW = 3

        const val CATA_ROW_H = 24f
        const val CATA_HEAD_INSET = 2f

        const val CLASS_CAP = 50

        // Fixed profile id, so the skull caches once. The value is the skin texture.
        const val CATACOMBS_HEAD_UUID = "2dc77ae7-9463-4802-9428-0c842274b567"
        const val CATACOMBS_HEAD =
            "eyJ0aW1lc3RhbXAiOjE1ODcwMTg2Nzk1NzYsInByb2ZpbGVJZCI6IjJkYzc3YWU3OTQ2MzQ4MDI5NDI4MGM4NDIyNzRiNTY3IiwicHJvZmlsZU5hbWUiOiJzYWR5MDYxMCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI1Njg5NWI5NjU5ODk2YWQ2NDdmNTg1OTkyMzhhZjUzMmQ0NmRiOWMxYjAzODliOGJiZWI3MDk5OWRhYjMzZCJ9fX0="

        val MAXED = Color(255, 215, 90)
    }
}
