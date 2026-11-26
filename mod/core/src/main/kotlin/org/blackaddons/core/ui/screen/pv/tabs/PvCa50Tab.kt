package org.blackaddons.core.ui.screen.pv.tabs

import org.blackaddons.api.skyblock.profile.DungeonClassStat
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.formatGrouped
import org.blackaddons.api.util.toRoman
import org.blackaddons.core.ui.screen.pv.PvFormat
import kotlin.math.roundToInt

internal class PvCa50Tab {
    private val label = I18n.scope("blackaddons.pv.ca50_detail")

    val widgets = WidgetHost()
    private val scroll = widgets.add(Scroll())
    private val targetInput = widgets.add(TextInput(
        placeholder = "50",
        maxLength = 5,
        charFilter = { it.isDigit() || it == '.' },
    ).apply {
        text = "50"
        onEnter = { focused = false }
        onTextChanged = { calculate() }
    })
    private val ringButton = widgets.add(Button("") {
        expertRing = !expertRing
        updateRingLabel()
        recalculate()
    })
    private val floorDropdown = Dropdown(maxVisibleItems = 8).apply {
        underline = true
        items = Ca50Floor.entries.map { Dropdown.Item(if (it == Ca50Floor.ENTRANCE) label("entrance", it.label) else it.label) }
        onSelect = { floor = Ca50Floor.entries[it]; recalculate() }
    }
    private val hecatombDropdown = Dropdown(maxVisibleItems = 6).apply {
        underline = true
        items = (0..10).map { Dropdown.Item(hecatombLabel(it)) }
        selectedIndex = 10
        onSelect = { hecatombLevel = it; recalculate() }
    }
    private val scarfAccessoryDropdown = Dropdown().apply {
        underline = true
        items = Ca50ScarfAccessory.entries.map { Dropdown.Item(scarfAccessoryLabel(it)) }
        selectedIndex = Ca50ScarfAccessory.GRIMOIRE.ordinal
        onSelect = { scarfAccessory = Ca50ScarfAccessory.entries[it]; recalculate() }
    }
    private val scarfAttributeDropdown = Dropdown(maxVisibleItems = 6).apply {
        underline = true
        items = (0..10).map { Dropdown.Item(attributeLabel(it)) }
        selectedIndex = 10
        onSelect = { scarfAttributeLevel = it; recalculate() }
    }
    private val globalDropdown = Dropdown().apply {
        underline = true
        items = Ca50GlobalBoost.entries.map { Dropdown.Item(globalLabel(it)) }
        onSelect = { globalBoost = Ca50GlobalBoost.entries[it]; recalculate() }
    }
    private val mayorDropdown = Dropdown().apply {
        underline = true
        items = Ca50Mayor.entries.map { Dropdown.Item(mayorLabel(it)) }
        onSelect = { mayor = Ca50Mayor.entries[it]; recalculate() }
    }
    private val dropdowns = listOf(
        floorDropdown,
        hecatombDropdown,
        scarfAccessoryDropdown,
        scarfAttributeDropdown,
        globalDropdown,
        mayorDropdown,
    ).onEach(widgets::add)

    private var floor = Ca50Floor.M7
    private var expertRing = true
    private var hecatombLevel = 10
    private var scarfAccessory = Ca50ScarfAccessory.GRIMOIRE
    private var scarfAttributeLevel = 10
    private var globalBoost = Ca50GlobalBoost.NONE
    private var mayor = Ca50Mayor.NONE
    private var currentMember: ProfileMember? = null
    private var renderedMember: ProfileMember? = null
    private var result: Ca50Result? = null

    init {
        updateRingLabel()
    }

    fun reset() {
        currentMember = null
        renderedMember = null
        scroll.reset()
        hide()
    }

    fun hide() {
        dropdowns.forEach(Dropdown::closeImmediately)
        widgets.focus(null)
    }

    fun render(x: Float, y: Float, width: Float, height: Float, member: ProfileMember, mouseX: Float, mouseY: Float) {
        currentMember = member
        if (renderedMember !== member) {
            renderedMember = member
            calculate(member)
        }

        Cards.scrolled(scroll, x, y, width, height) { w ->
            column(spacing = Cards.GAP, width = w) {
                add(configurationCard(w, mouseX, mouseY))
                add(result?.let { resultsNode(w, member, it) } ?: invalidTargetCard(w))
            }
        }
    }

    private fun configurationCard(width: Float, mouseX: Float, mouseY: Float): LayoutNode =
        Cards.card(width, I18n.trOr("blackaddons.pv.ca50_detail.configuration", "Configuration"), spacing = Theme.PADDING) { innerW ->
            val third = (innerW - CONTROL_GAP * 2f) / 3f
            val half = (innerW - CONTROL_GAP) / 2f
            row(spacing = CONTROL_GAP, width = innerW) {
                control(third, label("target", "Target Level")) { cx, cy, cw -> targetInput.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
                control(third, label("floor", "Floor")) { cx, cy, cw -> floorDropdown.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
                control(third, label("expert_ring", "Expert Ring")) { cx, cy, cw -> ringButton.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
            }
            row(spacing = CONTROL_GAP, width = innerW) {
                control(third, label("hecatomb", "Hecatomb")) { cx, cy, cw -> hecatombDropdown.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
                control(third, label("scarf_accessory", "Scarf Accessory")) { cx, cy, cw -> scarfAccessoryDropdown.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
                control(third, label("scarf_attribute", "Scarf Attribute")) { cx, cy, cw -> scarfAttributeDropdown.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
            }
            row(spacing = CONTROL_GAP, width = innerW) {
                control(half, label("global_boost", "Global Boost")) { cx, cy, cw -> globalDropdown.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
                control(half, label("mayor", "Mayor")) { cx, cy, cw -> mayorDropdown.render(cx, cy, cw, CONTROL_H, mouseX, mouseY) }
            }
        }

    /** A muted label over its control. */
    private fun LayoutBuilder.control(width: Float, label: String, draw: (x: Float, y: Float, width: Float) -> Unit) {
        val labelH = Render.textHeight(Theme.FONT_SMALL, Fonts.regular)
        cell(width, labelH + Theme.PADDING_SMALL + CONTROL_H) { cx, cy ->
            Render.text(label, cx, cy, TextStyle.SMALL_MUTED, maxWidth = width)
            draw(cx, cy + labelH + Theme.PADDING_SMALL, width)
        }
    }

    private fun resultsNode(width: Float, member: ProfileMember, result: Ca50Result): LayoutNode {
        val cardW = (width - Cards.GAP) / 2f
        val catacombs = listOf(
            Stat(label("current_level", "Current Level"), level(member.dungeons.catacombsLevel, member.dungeons.catacombsProgress)),
            Stat(label("target_level", "Target Level"), target(result)),
            Stat(label("target_xp", "Target XP"), result.targetXp.formatGrouped()),
            Stat(label("xp_per_run", "Estimated XP / Run"), result.catacombsXpPerRun.formatGrouped()),
            Stat(label("remaining_xp", "Remaining XP"), result.catacombsRemainingXp.formatGrouped()),
            Stat(label("runs_needed", "Runs Needed"), reached(result.catacombsRuns)),
        )
        val average = member.dungeons.classes.map { it.level + it.progress }.average().takeUnless(Double::isNaN) ?: 0.0
        val classes = listOf(
            Stat(label("current_average", "Current Average"), average.formatDecimals()),
            Stat(label("target_level", "Target Level"), target(result)),
            Stat(label("target_xp", "Target XP"), result.targetXp.formatGrouped()),
            Stat(label("class_xp_per_run", "Class XP / Run"), result.classXpPerRun.formatGrouped()),
            Stat(label("total_runs", "Total Runs"), reached(result.totalClassRuns)),
        )

        fun summary(title: String, metrics: List<Stat>, minHeight: Float) =
            Cards.card(cardW, title, minHeight = minHeight) { innerW -> stats(innerW, metrics, columns = 2) }
        // Equal heights, so the pair reads as one row even when one card has fewer metrics.
        val summaryH = maxOf(summary("", catacombs, 0f).height, summary("", classes, 0f).height)
        val byClass = member.dungeons.classes.associateBy(DungeonClassStat::dungeonClass)
        return column(spacing = Cards.GAP, width = width) {
            row(spacing = Cards.GAP, width = width) {
                add(summary(label("catacombs_goal", "Catacombs Goal"), catacombs, summaryH))
                add(summary(label("class_goal", "Class Goal"), classes, summaryH))
            }
            sectionHeader(width, label("class_breakdown", "Class Breakdown"))
            result.classes.forEach { classRow -> add(classCard(width, classRow, byClass[classRow.dungeonClass], result.targetXp)) }
        }
    }

    private fun classCard(width: Float, row: Ca50ClassResult, dungeonClass: DungeonClassStat?, targetXp: Double): LayoutNode {
        val color = row.dungeonClass.color
        val currentLevel = dungeonClass?.let { level(it.level, it.progress) } ?: "0"
        val detail = "${label("current_level", "Current Level")}: $currentLevel · ${label("remaining_xp", "Remaining XP")}: ${row.remainingXp.formatGrouped()}"
        val runs = "${label("runs", "Runs")}: ${row.runs.formatGrouped()}"
        return Cards.card(width) { innerW ->
            val titleH = Render.textHeight(Theme.FONT_BODY, Fonts.medium)
            cell(innerW, titleH) { cx, cy ->
                Render.circle(cx + CLASS_DOT, cy + titleH / 2f, CLASS_DOT, color)
                Render.text(PvFormat.dungeonClassName(row.dungeonClass.displayName), cx + CLASS_DOT * 2f + Theme.PADDING_SMALL, cy, TextStyle.BODY_PRIMARY_MEDIUM)
                val runsW = Render.textWidth(runs, Theme.FONT_SMALL, Fonts.medium)
                Render.text(runs, cx + innerW - runsW, Render.centerY(cy, titleH, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, color, Fonts.medium)
            }
            cell(innerW, Cards.ROW_H) { cx, cy ->
                val barX = cx + innerW * BAR_START
                Render.text(detail, cx, Render.centerY(cy, Cards.ROW_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED, maxWidth = barX - cx - Theme.PADDING)
                Cards.drawBar(barX, cy, cx + innerW - barX, Cards.ROW_H, (row.currentXp / targetXp).coerceIn(0.0, 1.0), color)
            }
        }
    }

    private fun invalidTargetCard(width: Float): LayoutNode = Cards.card(width) { innerW ->
        val detail = I18n.trOr(
            "blackaddons.pv.ca50_detail.invalid_target", "Enter a level from %s to %s.",
            PvCa50Calculator.MIN_TARGET_LEVEL.toInt(), PvCa50Calculator.MAX_TARGET_LEVEL.toInt(),
        )
        val titleH = Render.textHeight(Theme.FONT_BODY, Fonts.medium)
        cell(innerW, titleH) { cx, cy -> Render.textCentered(label("invalid_title", "Invalid target level"), cx + innerW / 2f, cy, TextStyle.BODY_PRIMARY_MEDIUM) }
        cell(innerW, Cards.ROW_H) { cx, cy -> Render.textCentered(detail, cx + innerW / 2f, Render.centerY(cy, Cards.ROW_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED) }
    }

    fun renderOverlay(mouseX: Float, mouseY: Float) = dropdowns.forEach { it.renderOverlay(mouseX, mouseY) }

    private fun calculate() {
        currentMember?.let(::calculate)
    }

    private fun calculate(member: ProfileMember) {
        val target = targetInput.text.toDoubleOrNull()
            ?.takeIf { it in PvCa50Calculator.MIN_TARGET_LEVEL..PvCa50Calculator.MAX_TARGET_LEVEL }
        targetInput.invalid = target == null
        if (target == null) {
            result = null
            return
        }
        result = PvCa50Calculator.calculate(
            member.dungeons.catacombsXp,
            member.dungeons.classes.associate { it.dungeonClass to it.xp },
            settings(target),
        )
    }

    private fun recalculate() = calculate()

    private fun settings(target: Double) = Ca50Settings(
        targetLevel = target,
        floor = floor,
        expertRing = expertRing,
        hecatombLevel = hecatombLevel,
        scarfAccessory = scarfAccessory,
        scarfAttributeLevel = scarfAttributeLevel,
        globalBoost = globalBoost,
        mayor = mayor,
    )

    private fun updateRingLabel() {
        ringButton.label = if (expertRing) label("yes", "Yes (+10%)") else label("no", "No")
    }

    private fun target(result: Ca50Result): String = result.targetLevel.formatDecimals()
    private fun reached(runs: Long): String = if (runs == 0L) label("reached", "Reached") else runs.formatGrouped()
    private fun level(level: Int, progress: Double): String = (level + progress).formatDecimals()
    private fun hecatombLabel(level: Int): String = if (level == 0) label("none", "None") else {
        val bonus = 0.4 + level * 0.16
        "${level.toRoman()} (+${bonus.formatDecimals()}%)"
    }

    private fun attributeLabel(level: Int): String =
        if (level == 0) label("none", "None") else "${level.toRoman()} (+${level * 2}%)"

    private fun scarfAccessoryLabel(accessory: Ca50ScarfAccessory): String = when (accessory) {
        Ca50ScarfAccessory.NONE -> label("none", "None")
        Ca50ScarfAccessory.STUDIES -> label("scarf_studies", "Studies") + " (+2%)"
        Ca50ScarfAccessory.THESIS -> label("scarf_thesis", "Thesis") + " (+4%)"
        Ca50ScarfAccessory.GRIMOIRE -> label("scarf_grimoire", "Grimoire") + " (+6%)"
    }

    private fun globalLabel(boost: Ca50GlobalBoost): String {
        val percent = ((boost.multiplier - 1.0) * 100).roundToInt()
        return if (percent == 0) "0%" else "+$percent%"
    }

    private fun mayorLabel(mayor: Ca50Mayor): String = when (mayor) {
        Ca50Mayor.NONE -> label("none", "None")
        Ca50Mayor.DERPY -> "Derpy (+50%)"
        Ca50Mayor.AURA -> "Aura (+55%)"
    }

    private companion object {
        const val CONTROL_H = 20f
        const val CONTROL_GAP = 8f
        const val CLASS_DOT = 3f
        /** Where the progress bar starts in a class card, as a share of its width. */
        const val BAR_START = 0.68f
    }
}
