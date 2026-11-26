package org.blackaddons.platform.impl.gui

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.I18n
import org.blackaddons.core.ui.screen.ClickGuiScreen
import org.blackaddons.platform.event.impl.RenderScreenEvent
import java.awt.Color
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * The clean title screen: text entries with icons in the middle, minor buttons as a row of icons under them, and the
 * legal lines behind a corner button. The vanilla widgets stay the source of truth: we move and draw them here, but
 * vanilla still handles their clicks, focus and narration, so buttons added by other mods end up in the layout too.
 */
internal object TitleMenu {

    enum class Title { BLACKADDONS, MINECRAFT, NONE }

    data class Options(
        val title: Title = Title.BLACKADDONS,
        val singleplayerText: Boolean = true,
        val hypixel: Boolean = true,
        val splash: Boolean = false,
        val confirmQuit: Boolean = false,
    )

    @Volatile
    var enabled = false

    @Volatile
    var options = Options()

    private val tr = I18n.scope("blackaddons.main_menu")

    private const val HYPIXEL_HOST = "mc.hypixel.net"
    private const val WEBSITE_URL = "https://dash.blackaddons.com"

    internal const val LOGO_SIZE = 64f
    internal const val LOGO_GAP = 6f
    internal const val LOGO_PATH = "assets/blackaddons/window_icon/large/512.png"
    internal const val TITLE_SIZE = 22f
    internal const val OUTLINE = 0.75f
    internal const val SHADOW_DROP = 1.5f
    internal const val VERSION_SIZE = 8f
    internal const val ITEM_SIZE = 13f
    internal const val ITEM_HEIGHT = 20
    private const val ITEM_GAP = 2
    internal const val ITEM_ICON = 12f
    internal const val ITEM_PAD = 10f
    internal const val CELL = 20
    private const val CELL_GAP = 4
    internal const val CELL_ICON = 12f
    private const val SECTION_GAP = 14
    internal const val TITLE_GAP = 14
    internal const val LEGAL_SIZE = 12
    internal const val LEGAL_TEXT = 8f
    internal const val LEGAL_LINE_GAP = 2f
    private const val EDGE = 6
    private const val HOVER_RATE = 14.0
    internal const val UNDERLINE = 1.5f
    internal const val TOOLTIP_PAD_X = 5f
    internal const val TOOLTIP_PAD_Y = 3f
    internal const val TOOLTIP_GAP = 4f

    // IAS labels its button with a literal, not a translation key.
    private const val IAS_LABEL = "In-Game Account Switcher"

    // The space the vanilla logo takes, kept clear while the logo stays. In game units: vanilla draws that logo.
    private const val LOGO_BOTTOM = 30 + 44 + 14

    internal enum class Role { TEXT, ICON, FOREIGN_ICON, LEGAL_TOGGLE, LEGAL_LINK, CONFIRM, HIDDEN }

    internal class Slot(
        val widget: AbstractWidget,
        val role: Role,
        val icon: VectorIcon?,
        val order: Int,
        val label: String,
        val danger: Boolean = false,
    )

    private class Own(val icon: VectorIcon?, val order: Int, val role: Role, val danger: Boolean = false)

    // Widgets this screen draws itself. Vanilla skips rendering them (AbstractWidgetRenderMixin).
    private val owned: MutableSet<AbstractWidget> = Collections.newSetFromMap(IdentityHashMap())
    internal val hover = IdentityHashMap<AbstractWidget, Float>()
    internal var slots = emptyList<Slot>()
    internal var legalOpen = false
    private var quitConfirmOpen = false
    private val quitCard = ConfirmCard()
    private val frameClock = FrameClock(cap = 100.milliseconds)

    private val ours = IdentityHashMap<AbstractWidget, Own>()

    /**
     * Game units per menu unit: the same factor as every BlackAddons window, so the menu follows the screen size, the
     * GUI Scale and the Interface Scale setting instead of shrinking on a large monitor.
     */
    private var scale = 1f

    internal fun sf(value: Float): Float = value * scale
    internal fun si(value: Int): Int = (value * scale).roundToInt()

    fun install() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is TitleScreen && enabled) addOwnButtons(screen)
        }
        // HIGH, so it runs before the toasts, which draw on the same event and have to stay on top.
        EventListener(RenderScreenEvent::class.java, EventPriority.HIGH) {
            val screen = event.screen
            if (screen is TitleScreen && active(screen)) draw(screen)
        }.register()
    }

    /** True for a widget this screen draws itself. Called for every widget every frame, so it stays a set lookup. */
    @JvmStatic
    fun owns(widget: AbstractWidget): Boolean = enabled && widget in owned

    @JvmStatic
    fun showLogo(): Boolean = !enabled || options.title == Title.MINECRAFT

    @JvmStatic
    fun showSplash(): Boolean = !enabled || options.splash

    /** Vanilla's version line and the Realms notification badges, which both depend on the old layout. */
    @JvmStatic
    fun showVanillaExtras(): Boolean = !enabled

    /**
     * Places every widget before the screen extracts them. Runs every frame, because mods add buttons at any point of
     * init.
     */
    @JvmStatic
    fun arrange(screen: TitleScreen) {
        if (!active(screen)) {
            owned.clear()
            return
        }
        scale = UiManager.normalizedScale
        slots = Screens.getWidgets(screen).mapNotNull(::classify)
        owned.clear()
        slots.filter { it.role != Role.FOREIGN_ICON }.forEach { owned += it.widget }
        layout(screen)
    }

    private fun active(screen: TitleScreen) = enabled && Screens.getWidgets(screen).isNotEmpty()

    private fun addOwnButtons(screen: TitleScreen) {
        val buttons = Screens.getWidgets(screen)
        ours.clear()
        quitConfirmOpen = false
        if (options.hypixel) {
            buttons += own(tr("hypixel", "Hypixel"), Own(LucideIcons.swords, TEXT_HYPIXEL, Role.TEXT)) { connect(screen, HYPIXEL_HOST) }
        }
        buttons += own(tr("blackaddons", "BlackAddons"), Own(LucideIcons.sparkles, ICON_BLACKADDONS, Role.ICON)) { ClickGuiScreen.open() }
        buttons += own(tr("website", "Website"), Own(LucideIcons.globe, ICON_WEBSITE, Role.ICON)) { Platform.desktop.openUrl(WEBSITE_URL) }
        buttons += own(tr("legal", "Legal"), Own(LucideIcons.copyright, LEGAL, Role.LEGAL_TOGGLE)) { legalOpen = !legalOpen }
        // Opt-in: our quit button asks first, and vanilla's own button is hidden while it does (Role.HIDDEN).
        if (options.confirmQuit) {
            val quit = Component.translatable("menu.quit").string
            buttons += own(quit, Own(LucideIcons.power, ICON_QUIT, Role.ICON, danger = true)) { quitConfirmOpen = !quitConfirmOpen }
            buttons += own(tr("cancel", "Cancel"), Own(null, CONFIRM_CANCEL, Role.CONFIRM)) { quitConfirmOpen = false }
            buttons += own(tr("quit", "Quit"), Own(null, CONFIRM_QUIT, Role.CONFIRM, danger = true)) { Minecraft.getInstance().stop() }
        }
    }

    private fun own(label: String, own: Own, onPress: () -> Unit): Button =
        Button.builder(Component.literal(label)) { onPress() }.bounds(0, 0, CELL, CELL).build()
            .also { ours[it] = own }

    private fun connect(screen: TitleScreen, host: String) {
        val mc = Minecraft.getInstance()
        ConnectScreen.startConnecting(
            screen, mc, ServerAddress.parseString(host), ServerData("Hypixel", host, ServerData.Type.OTHER), false, null,
        )
    }

    // Order values: text entries are 0-99, icons are 100 and up, then the legal pair.
    private const val TEXT_SINGLEPLAYER = 0
    private const val TEXT_MULTIPLAYER = 10
    private const val TEXT_HYPIXEL = 20
    private const val TEXT_FOREIGN = 30
    private const val TEXT_OPTIONS = 40
    private const val ICON_SINGLEPLAYER = 100
    private const val ICON_REALMS = 110
    private const val ICON_FRIENDS = 120
    private const val ICON_ACCOUNTS = 125
    private const val ICON_MODS = 130
    private const val ICON_BLACKADDONS = 140
    private const val ICON_WEBSITE = 150
    private const val ICON_FOREIGN = 160
    private const val ICON_LANGUAGE = 170
    private const val ICON_ACCESSIBILITY = 180
    private const val ICON_QUIT = 190
    private const val CONFIRM_CANCEL = 200
    private const val CONFIRM_QUIT = 210
    private const val LEGAL = 1000

    private fun classify(widget: AbstractWidget): Slot? {
        val label = widget.message.string
        // Vanilla's "Options..." promises another screen, but on a bare entry the dots look like a typo.
        val entry = label.trimEnd('.', '\u2026').trimEnd()
        // The quick toggle card places and draws its own button.
        if (TitleQuickToggles.owns(widget)) return null
        ours[widget]?.let { return Slot(widget, it.role, it.icon, it.order, label, it.danger) }
        if (widget is ImageButton && label == IAS_LABEL) {
            return Slot(widget, Role.ICON, LucideIcons.userRoundCog, ICON_ACCOUNTS, accountLabel(label))
        }
        if (widget is PlainTextButton) return Slot(widget, Role.LEGAL_LINK, null, LEGAL + 1, label)
        if (widget.javaClass.simpleName == "FriendsButton") return Slot(widget, Role.ICON, LucideIcons.usersRound, ICON_FRIENDS, label)
        return when ((widget.message.contents as? TranslatableContents)?.key) {
            "menu.singleplayer" ->
                if (options.singleplayerText) Slot(widget, Role.TEXT, LucideIcons.user, TEXT_SINGLEPLAYER, entry)
                else Slot(widget, Role.ICON, LucideIcons.user, ICON_SINGLEPLAYER, label)
            "menu.multiplayer" -> Slot(widget, Role.TEXT, LucideIcons.server, TEXT_MULTIPLAYER, entry)
            "menu.playdemo", "menu.resetdemo" -> Slot(widget, Role.TEXT, LucideIcons.user, TEXT_SINGLEPLAYER, entry)
            "menu.options" -> Slot(widget, Role.TEXT, LucideIcons.settings, TEXT_OPTIONS, entry)
            "menu.online" -> Slot(widget, Role.ICON, LucideIcons.cloud, ICON_REALMS, label)
            "modmenu.title" -> Slot(widget, Role.ICON, LucideIcons.extras, ICON_MODS, label)
            "options.language" -> Slot(widget, Role.ICON, LucideIcons.languages, ICON_LANGUAGE, label)
            "options.accessibility" -> Slot(widget, Role.ICON, LucideIcons.accessibility, ICON_ACCESSIBILITY, label)
            "menu.quit" ->
                if (options.confirmQuit) Slot(widget, Role.HIDDEN, null, ICON_QUIT, label)
                else Slot(widget, Role.ICON, LucideIcons.power, ICON_QUIT, label, danger = true)
            /*
             * A widget from another mod: text buttons join the list, and anything else keeps its own look in the icon
             * row.
             */
            else -> if (widget is Button && label.isNotBlank() && widget.width > CELL) {
                Slot(widget, Role.TEXT, null, TEXT_FOREIGN, entry)
            } else {
                Slot(widget, Role.FOREIGN_ICON, null, ICON_FOREIGN, label)
            }
        }
    }

    private fun accountLabel(fallback: String): String =
        Platform.session.username?.let { tr("account", "Account: %s", it) } ?: fallback

    internal fun texts() = slots.filter { it.role == Role.TEXT }.sortedBy { it.order }
    private fun icons() = slots.filter { it.role == Role.ICON || it.role == Role.FOREIGN_ICON }.sortedBy { it.order }

    private fun titleHeight(): Int = when (options.title) {
        Title.BLACKADDONS ->
            (sf(LOGO_SIZE) + sf(LOGO_GAP) + Render.textHeight(sf(TITLE_SIZE), Fonts.bold) + Render.textHeight(sf(VERSION_SIZE), Fonts.regular)).toInt() + si(TITLE_GAP)
        Title.MINECRAFT, Title.NONE -> 0
    }

    private fun layout(screen: TitleScreen) {
        val texts = texts()
        val icons = icons()
        val listHeight = texts.size * si(ITEM_HEIGHT) + (texts.size - 1).coerceAtLeast(0) * si(ITEM_GAP)
        val rowHeight = if (icons.isEmpty()) 0 else si(CELL) + si(SECTION_GAP)
        val block = titleHeight() + listHeight + rowHeight
        val top = if (options.title == Title.MINECRAFT) {
            maxOf(LOGO_BOTTOM, (screen.height - block) / 2)
        } else {
            (screen.height - block) / 2
        }

        var y = top + titleHeight()
        texts.forEach { slot ->
            val width = (itemWidth(slot) + 0.5f).toInt()
            slot.widget.setSize(width, si(ITEM_HEIGHT))
            slot.widget.setPosition((screen.width - width) / 2, y)
            y += si(ITEM_HEIGHT) + si(ITEM_GAP)
        }

        val rowWidth = icons.sumOf { it.widget.width.coerceAtLeast(si(CELL)) } + (icons.size - 1).coerceAtLeast(0) * si(CELL_GAP)
        var x = (screen.width - rowWidth) / 2
        val rowY = y - si(ITEM_GAP) + si(SECTION_GAP)
        icons.forEach { slot ->
            if (slot.role == Role.ICON) slot.widget.setSize(si(CELL), si(CELL))
            slot.widget.setPosition(x, rowY + (si(CELL) - slot.widget.height) / 2)
            x += slot.widget.width + si(CELL_GAP)
        }

        slots.firstOrNull { it.role == Role.LEGAL_TOGGLE }?.widget?.let {
            it.setSize(si(LEGAL_SIZE), si(LEGAL_SIZE))
            it.setPosition(si(EDGE), screen.height - si(EDGE) - si(LEGAL_SIZE))
        }
        slots.firstOrNull { it.role == Role.LEGAL_LINK }?.let { slot ->
            // Sized to our font, not vanilla's, so the click area matches what's drawn.
            val width = Render.textWidth(slot.label, sf(LEGAL_TEXT), Fonts.regular).toInt() + 1
            val height = Render.textHeight(sf(LEGAL_TEXT), Fonts.regular).toInt() + 1
            slot.widget.visible = legalOpen
            slot.widget.setSize(width, height)
            slot.widget.setPosition(si(EDGE) + si(LEGAL_SIZE) + si(EDGE), screen.height - si(EDGE) - si(LEGAL_SIZE) / 2 - height / 2)
        }
        slots.filter { it.role == Role.HIDDEN }.forEach { it.widget.visible = false }
        layoutPopover(screen)
    }

    // "Quit the game?" next to the quit icon: Cancel and then Quit.
    private fun layoutPopover(screen: TitleScreen) {
        val confirm = slots.filter { it.role == Role.CONFIRM }.sortedBy { it.order }.map { ConfirmCard.Choice(it.widget, it.label, it.danger) }
        val anchor = slots.firstOrNull { it.role == Role.ICON && it.order == ICON_QUIT }?.widget
        quitCard.layout(quitConfirmOpen, anchor, confirmQuestion(), confirm, screen.width, screen.height, scale)
    }

    internal fun confirmQuestion() = tr("confirm_quit", "Quit the game?")

    private fun itemWidth(slot: Slot): Float {
        val icon = if (slot.icon != null) sf(ITEM_ICON) + sf(ITEM_PAD) / 2 else 0f
        return sf(ITEM_PAD) * 2 + icon + Render.textWidth(slot.label, sf(ITEM_SIZE), Fonts.medium)
    }

    private fun draw(screen: TitleScreen) {
        val dt = frameClock.tick()
        if (options.title == Title.BLACKADDONS) drawTitle(screen)
        slots.forEach { slot ->
            val target = if (slot.widget.active && highlighted(slot.widget)) 1f else 0f
            val current = hover[slot.widget] ?: 0f
            val eased = current + (target - current) * (1 - exp(-dt * HOVER_RATE)).toFloat()
            hover[slot.widget] = eased
            when (slot.role) {
                Role.TEXT -> drawText(slot, eased)
                Role.ICON -> drawIcon(slot, eased)
                Role.LEGAL_TOGGLE -> drawLegalToggle(slot, eased)
                Role.LEGAL_LINK -> if (legalOpen) drawLegal(slot, eased)
                Role.FOREIGN_ICON, Role.CONFIRM, Role.HIDDEN -> Unit
            }
        }
        quitCard.draw()
        // Tooltips go last, over the row, and none show while the quit card covers that spot.
        if (!quitCard.shown) slots.firstOrNull { it.role == Role.ICON && highlighted(it.widget) }?.let(::drawTooltip)
        hover.keys.retainAll(slots.map { it.widget }.toSet())
    }

    internal val CONTOUR = Color(8, 4, 14, 235)
    internal val SHADOW = Color(0, 0, 0, 120)
    internal val OUTLINE_OFFSETS = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

}
