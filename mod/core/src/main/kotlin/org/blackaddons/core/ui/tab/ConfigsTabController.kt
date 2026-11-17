package org.blackaddons.core.ui.tab

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.config.portable.ConfigPortable
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.log.Log
import org.blackaddons.core.ui.screen.portable.PortableExportModal

internal class ConfigsTabController : TabController {

    private data class Section(val titleKey: String, val rows: List<Row>)

    /**
     * [destructive] rows are red and ask first: in a popover by the button, or full-screen for [global] rows, which
     * reach every feature. (The old confirm inside the button got cut off.)
     */
    private data class Row(
        val labelKey: String,
        val buttonKey: String,
        val destructive: Boolean = false,
        val global: Boolean = false,
        val onClick: () -> Unit,
    ) {
        val hoverAnim = Animation(0f, Theme.ANIM_HOVER.toLong())
    }

    private val sections = listOf(
        Section("blackaddons.configs.section.configuration", listOf(
            Row("blackaddons.configs.save_now.label", "blackaddons.configs.save_now.button") { BlackaddonsConfig.save() },
            // Reloading discards unsaved work.
            Row(
                "blackaddons.configs.reload.label", "blackaddons.configs.reload.button",
                destructive = true,
            ) { BlackaddonsConfig.load() },
            Row(
                "blackaddons.configs.reset_settings.label", "blackaddons.configs.reset_settings.button",
                destructive = true, global = true,
            ) { resetSettings() },
        )),
        Section("blackaddons.configs.section.sharing", listOf(
            Row("blackaddons.configs.share.label", "blackaddons.configs.share.button") {
                PortableExportModal.share(I18n.tr("blackaddons.configs.share.name"), ConfigPortable.export())
            },
            Row("blackaddons.configs.paste.label", "blackaddons.configs.paste.button") {
                PortableImports.open(ConfigPortable, Platform.clipboard.paste())
            },
        )),
        Section("blackaddons.configs.section.hud", listOf(
            Row(
                "blackaddons.configs.reset_huds.label", "blackaddons.configs.reset_huds.button",
                destructive = true,
            ) { resetHuds() },
        )),
    )

    private val sectionGap = 14f
    private val rowGap = 6f
    private val rowHeight = Theme.FEATURE_ROW_HEIGHT
    private val buttonH = rowHeight - 8f

    /** One width for the column: the widest measured label, because translations differ. */
    private val buttonW: Float
        get() {
            val widest = sections.asSequence()
                .flatMap { it.rows.asSequence() }
                .maxOf { Render.textWidth(I18n.tr(it.buttonKey), Theme.FONT_SMALL, Fonts.medium) }
            return (widest + BUTTON_PAD * 2f).coerceIn(72f, 160f)
        }

    private val widgets = WidgetHost()

    override fun render(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        var ry = y
        for (section in sections) {
            Render.text(
                I18n.tr(section.titleKey),
                x + Theme.PADDING_LARGE, ry,
                TextStyle.SMALL_MUTED_BOLD,
            )
            ry += Render.textHeight(Theme.FONT_SMALL, Fonts.bold) + 6f
            for (row in section.rows) {
                renderRow(row, x + Theme.PADDING_LARGE, ry, width - Theme.PADDING_LARGE * 2f, mouseX, mouseY)
                ry += rowHeight + rowGap
            }
            ry += sectionGap
        }
    }

    private fun renderRow(row: Row, rx: Float, ry: Float, rw: Float, mouseX: Float, mouseY: Float) {
        val bx = rx + rw - buttonW
        val by = centered(ry, rowHeight, buttonH)
        val hovered = widgets.clickable(bx, by, buttonW, buttonH) {
            if (row.destructive) confirm(row, bx, by) else safeRun(row.onClick)
        }
        row.hoverAnim.animateTo(if (hovered) 1f else 0f)

        Render.rect(rx, ry, rw, rowHeight, Theme.SURFACE.withAlpha(60), Theme.RADIUS)

        val full = I18n.tr(row.labelKey)
        val labelRoom = bx - (rx + Theme.PADDING) - Theme.PADDING
        val label = Render.ellipsize(full, labelRoom, Theme.FONT_SMALL, Fonts.regular)
        if (label != full && mouseX in rx..bx && mouseY in ry..(ry + rowHeight)) Tooltip.show(full)

        Render.text(
            label,
            rx + Theme.PADDING,
            Render.centerY(ry, rowHeight, TextStyle.SMALL_SECONDARY),
            TextStyle.SMALL_SECONDARY,
        )

        val btnBg = when {
            row.destructive && hovered -> Theme.DANGER
            hovered -> Theme.ACCENT
            row.destructive -> Theme.DANGER.withAlpha(70)
            else -> Theme.SURFACE_ACTIVE
        }
        Render.rect(bx, by, buttonW, buttonH, btnBg, Theme.RADIUS_SMALL)
        val buttonFull = I18n.tr(row.buttonKey)
        val buttonText = Render.ellipsize(buttonFull, buttonW - BUTTON_PAD * 2f, Theme.FONT_SMALL, Fonts.medium)
        if (hovered && buttonText != buttonFull) Tooltip.show(buttonFull)
        val tw = Render.textWidth(buttonText, Theme.FONT_SMALL, Fonts.medium)
        Render.text(
            buttonText,
            centered(bx, buttonW, tw),
            Render.centerY(by, buttonH, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL,
            if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
            Fonts.medium,
        )
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean =
        widgets.mouseClicked(mouse)

    private fun confirm(row: Row, buttonX: Float, buttonY: Float) {
        val run = { safeRun(row.onClick) }
        if (row.global) {
            UiManager.confirm(
                title = I18n.tr(row.buttonKey),
                message = I18n.tr(row.labelKey),
                onConfirm = run,
            )
        } else {
            UiManager.confirmPopover(buttonX, buttonY, buttonW, buttonH, onConfirm = run)
        }
    }

    private fun resetSettings() {
        FeatureManager.features.forEach(Feature::resetSettings)
    }

    private fun resetHuds() {
        for (hud in FeatureManager.hudElements) hud.resetToDefault()
    }

    private fun safeRun(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.main.error("Config action threw", t)
        }
    }

    companion object {
        private const val BUTTON_PAD = 12f
    }
}
