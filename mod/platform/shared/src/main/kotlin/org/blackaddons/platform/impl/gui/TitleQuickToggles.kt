package org.blackaddons.platform.impl.gui

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.ui.screen.ClickGuiScreen
import org.blackaddons.platform.compat.openScreen
import org.blackaddons.platform.event.impl.RenderScreenEvent
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * The [Categories.Client.Menu] features as switches behind a corner button of the title screen, with or without the
 * clean menu, plus a link to their settings. They start off, so this is where a player finds them. Real buttons sit
 * underneath, like in [TitleMenu], so clicks, Tab and narration stay vanilla's.
 */
internal object TitleQuickToggles {

    private val tr = I18n.scope("blackaddons.main_menu")

    private const val CELL = 20
    private const val CELL_ICON = 12f
    private const val EDGE = 6
    private const val GAP = 4
    private const val PAD = 6
    private const val ROW_HEIGHT = 16
    private const val ROW_INSET = 3
    private const val TEXT = 9f
    private const val CAPTION = 8f
    private const val SWITCH_WIDTH = 20f
    private const val SWITCH_HEIGHT = 11f
    private const val SWITCH_GAP = 12f
    private const val LINK_ICON = 10f
    private const val DIVIDER = 2 * GAP + 1
    private const val TOOLTIP_PAD = 5f
    private const val TOOLTIP_PAD_Y = 3f
    private const val RATE = 14.0

    // feature == null is the settings link.
    private class Row(val widget: Button, val label: String, val feature: Feature?)

    /**
     * Widgets drawn here. Vanilla skips drawing them (AbstractWidgetRenderMixin), and TitleMenu leaves them where they
     * are.
     */
    private val owned: MutableSet<AbstractWidget> = Collections.newSetFromMap(IdentityHashMap())
    private val hover = IdentityHashMap<AbstractWidget, Float>()
    private val switches = HashMap<String, Float>()
    private var corner: Button? = null
    private var rows = emptyList<Row>()
    private var open = false
    private val frameClock = FrameClock(cap = 100.milliseconds)

    // Measured once per screen init, not per frame, in menu units before the scale.
    private var cardWidth = 0
    private var tooltipWidth = 0f

    // Game units per menu unit, same as TitleMenu.
    private var scale = 1f

    private fun sf(value: Float): Float = value * scale
    private fun si(value: Int): Int = (value * scale).roundToInt()

    fun install() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ -> if (screen is TitleScreen) addButtons(screen) }
        // HIGH and installed after TitleMenu, so it draws over the menu and under the toasts.
        EventListener(RenderScreenEvent::class.java, EventPriority.HIGH) {
            (event.screen as? TitleScreen)?.let(::draw)
        }.register()
    }

    /** True for a widget this card draws itself. Called for every widget every frame, so it stays a set lookup. */
    @JvmStatic
    fun owns(widget: AbstractWidget): Boolean = widget in owned

    private fun menuFeatures(): List<Feature> =
        FeatureManager.features.filter { it.section == Categories.Client.Menu }.sortedBy { it.translatedName }

    private fun addButtons(screen: TitleScreen) {
        owned.clear()
        val customize = tr("customize", "Customize Menu")
        val toggle = button(customize) { open = !open }
        rows = menuFeatures().map { feature -> row(feature.translatedName, feature) { flip(feature) } } +
            row(tr("all_settings", "All Settings"), null) { ClickGuiScreen.open(Categories.Client.Menu) }
        corner = toggle

        val labels = rows.map { it.label to (if (it.feature != null) SWITCH_GAP + SWITCH_WIDTH else GAP + LINK_ICON) }
        val widest = maxOf(
            labels.maxOfOrNull { (label, extra) -> Render.textWidth(label, TEXT, Fonts.medium) + extra } ?: 0f,
            Render.textWidth(customize, CAPTION, Fonts.medium),
        )
        cardWidth = (widest + 2 * (PAD + ROW_INSET) + 0.5f).toInt()
        tooltipWidth = Render.textWidth(customize, CAPTION, Fonts.regular)

        val widgets = Screens.getWidgets(screen)
        widgets += toggle
        rows.forEach { widgets += it.widget }
        layout(screen)
    }

    private fun button(label: String, onPress: () -> Unit): Button =
        Button.builder(Component.literal(label)) { onPress() }.bounds(0, 0, CELL, CELL).build().also { owned += it }

    private fun row(label: String, feature: Feature?, onPress: () -> Unit) = Row(button(label, onPress), label, feature)

    private fun flip(feature: Feature) {
        feature.toggle()
        BlackaddonsConfig.save()
        // A fresh title screen, so the clean menu's own buttons come and go with it. The card stays open.
        Minecraft.getInstance().openScreen(TitleScreen())
    }

    private fun layout(screen: TitleScreen) {
        scale = UiManager.normalizedScale
        val toggle = corner ?: return
        toggle.setSize(si(CELL), si(CELL))
        toggle.setPosition(screen.width - si(EDGE) - si(CELL), si(EDGE))
        rows.forEach { it.widget.visible = open }
        if (!open) return
        val x = screen.width - si(EDGE) - si(cardWidth)
        var y = cardTop() + si(PAD) + captionHeight() + si(GAP)
        rows.forEach { row ->
            if (row.feature == null) y += si(DIVIDER)
            row.widget.setSize(si(cardWidth) - 2 * si(ROW_INSET), si(ROW_HEIGHT))
            row.widget.setPosition(x + si(ROW_INSET), y)
            y += si(ROW_HEIGHT)
        }
    }

    private fun cardTop() = si(EDGE) + si(CELL) + si(GAP)

    private fun captionHeight() = Render.textHeight(sf(CAPTION), Fonts.medium).toInt()

    private fun cardHeight() = si(PAD) + captionHeight() + si(GAP) + rows.size * si(ROW_HEIGHT) + si(DIVIDER) + si(PAD)

    // Focus highlights a widget only during keyboard navigation, same as TitleMenu.
    private fun highlighted(widget: AbstractWidget): Boolean =
        widget.isHovered || (widget.isFocused && Minecraft.getInstance().lastInputType.isKeyboard)

    private fun draw(screen: TitleScreen) {
        val toggle = corner ?: return
        // A screen that got initialized again without us has no such button to click.
        if (toggle !in Screens.getWidgets(screen)) return
        layout(screen)
        val dt = frameClock.tick()

        val lit = ease(toggle, if (open || highlighted(toggle)) 1f else 0f, dt)
        Render.rect(toggle.x, toggle.y, si(CELL), si(CELL), lerpColor(Theme.SURFACE.withAlpha(150), Theme.ACCENT.withAlpha(70), lit), radius = sf(Theme.RADIUS_SMALL))
        val inset = (si(CELL) - sf(CELL_ICON)) / 2
        Render.icon(LucideIcons.appWindow, toggle.x + inset, toggle.y + inset, sf(CELL_ICON), lerpColor(Theme.TEXT_SECONDARY, Theme.ACCENT, lit))

        if (open) drawCard(screen, dt) else if (highlighted(toggle)) drawTooltip(toggle)
        hover.keys.retainAll(owned)
    }

    private fun drawCard(screen: TitleScreen, dt: Double) {
        val x = (screen.width - si(EDGE) - si(cardWidth)).toFloat()
        val y = cardTop().toFloat()
        Render.rect(x, y, si(cardWidth), cardHeight(), Theme.SURFACE.withAlpha(240), radius = sf(Theme.RADIUS_SMALL))
        Render.hollowRect(x, y, si(cardWidth), cardHeight(), 1f, Theme.BORDER, radius = sf(Theme.RADIUS_SMALL))
        Render.text(tr("customize", "Customize Menu"), x + si(PAD) + si(ROW_INSET), y + si(PAD), sf(CAPTION), Theme.TEXT_MUTED, Fonts.medium)

        rows.forEach { row ->
            val w = row.widget
            val lit = ease(w, if (highlighted(w)) 1f else 0f, dt)
            if (row.feature == null) {
                Render.rect(x + si(PAD), w.y - si(DIVIDER) / 2f - 0.5f, si(cardWidth) - 2f * si(PAD), 1f, Theme.BORDER)
            }
            if (lit > 0.01f) Render.rect(w.x, w.y, w.width, w.height, Theme.SURFACE_HOVER.withAlpha(lit), radius = sf(Theme.RADIUS_SMALL))
            val textX = w.x + si(PAD).toFloat()
            val right = w.x + w.width - si(PAD).toFloat()
            Render.text(row.label, textX, Render.centerY(w.y, si(ROW_HEIGHT), sf(TEXT), Fonts.medium), sf(TEXT), Theme.TEXT_PRIMARY, Fonts.medium)
            val feature = row.feature
            if (feature != null) {
                val on = ease(feature.id, if (feature.enabled) 1f else 0f, dt)
                Render.toggleSwitch(right - sf(SWITCH_WIDTH), w.y + (si(ROW_HEIGHT) - sf(SWITCH_HEIGHT)) / 2, sf(SWITCH_WIDTH), sf(SWITCH_HEIGHT), on)
            } else {
                val color = lerpColor(Theme.TEXT_SECONDARY, Theme.ACCENT, lit)
                Render.icon(LucideIcons.settings, right - sf(LINK_ICON), w.y + (si(ROW_HEIGHT) - sf(LINK_ICON)) / 2, sf(LINK_ICON), color)
            }
        }
    }

    // To the left of the button, because the button sits in the corner with no room to the right or above.
    private fun drawTooltip(toggle: AbstractWidget) {
        val height = Render.textHeight(sf(CAPTION), Fonts.regular) + 2 * sf(TOOLTIP_PAD_Y)
        val width = sf(tooltipWidth) + 2 * sf(TOOLTIP_PAD)
        val x = toggle.x - si(GAP) - width
        val y = toggle.y + (si(CELL) - height) / 2
        Render.rect(x, y, width, height, Theme.SURFACE.withAlpha(230), radius = sf(Theme.RADIUS_SMALL))
        Render.text(tr("customize", "Customize Menu"), x + sf(TOOLTIP_PAD), y + sf(TOOLTIP_PAD_Y), sf(CAPTION), Theme.TEXT_PRIMARY, Fonts.regular)
    }

    private fun ease(widget: AbstractWidget, target: Float, dt: Double): Float =
        approach(hover[widget] ?: target, target, dt).also { hover[widget] = it }

    // Keyed by feature, so a switch keeps animating across the rebuilt screen after a flip.
    private fun ease(featureId: String, target: Float, dt: Double): Float =
        approach(switches[featureId] ?: target, target, dt).also { switches[featureId] = it }

    private fun approach(current: Float, target: Float, dt: Double): Float =
        current + (target - current) * (1 - exp(-dt * RATE)).toFloat()
}
