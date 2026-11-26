package org.blackaddons.platform.ui.skin

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.blackaddons.api.ui.anim.AnimationMap
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.GlassCheck
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.color.fade
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.impl.gui.MinecraftWindow
import org.blackaddons.platform.ui.render.BakedGlass
import org.blackaddons.platform.ui.render.BakedGlass.Corner
import kotlin.math.ceil

/**
 * Vanilla GUI sprites in [Glass], looked up by sprite id: widgets on any screen, the sprites container screens, menus,
 * toasts and tooltips draw, and the hotbar, effect and boss bar sprites of the HUD. The `blitSprite` mixins call
 * [sprite] and [partSprite]; unknown sprites cost one map lookup. Render thread only.
 */
object GuiSkin {

    @JvmField var widgets = false
    @JvmField var minecraftScreensOnly = false
    @JvmField var toasts = false
    @JvmField var menus = false
    @JvmField var tooltips = false
    @JvmField var hotbar = false
    @JvmField var bossBar = false

    private enum class Group { WIDGET, CONTAINER, TOAST, MENU, TOOLTIP, HOTBAR, BOSS_BAR }

    private enum class Kind { PANEL, ICON, ARROW, OUTLINE, HIDE, HOTBAR, OFFHAND_LEFT, OFFHAND_RIGHT, BAR, TAB, CHECKBOX, CHECKMARK, SLOT }

    private enum class Edge { DOWN, UP, RIGHT, LEFT }

    private class Skin(
        val group: Group,
        val style: Glass = Glass.BUTTON,
        val kind: Kind = Kind.PANEL,
        val lit: Boolean = false,
        val dim: Boolean = false,
        val danger: Boolean = false,
        val inset: Int = 0,
        val selected: Boolean = false,
        val edge: Edge = Edge.DOWN,
        val overlap: Int = 0,
        val bar: Boolean = true,
        val corner: Corner? = null,
        val color: Int = 0,
    )

    private val NONE = Skin(Group.WIDGET)
    private val skins = HashMap<Identifier, Skin>()
    private val hovers = AnimationMap<Long>()
    private val checks = AnimationMap<Long>(GlassCheck.DURATION_MS, settled = true)
    private var checkScreen: Screen? = null
    private var scopeScreen: Screen? = null
    private var scopeVanilla = true

    /** True when the sprite was drawn as glass and vanilla's draw should be skipped. */
    fun sprite(g: GuiGraphicsExtractor, id: Identifier, x: Int, y: Int, w: Int, h: Int, color: Int): Boolean {
        val skin = skinFor(id) ?: return false
        if (skin.kind == Kind.BAR) return false
        val alpha = (color ushr 24) / 255f * if (skin.dim) DIM else 1f
        val i = skin.inset
        when (skin.kind) {
            Kind.PANEL -> SkinGlass.draw(g, x + i, y + i, w - i * 2, h - i * 2, skin.style, hover(skin, x, y, w, h), alpha)
            Kind.ICON -> {
                val icon = Decor.sprite(id, w, h) ?: return false
                SkinGlass.draw(g, x, y, w, h, skin.style, hover(skin, x, y, w, h), alpha)
                if (icon !== Decor.EMPTY) g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, w, h, w, h, w, h, ARGB.white(alpha))
            }
            Kind.ARROW -> {
                val shape = Decor.silhouette(id, w, h) ?: return false
                val tint = if (skin.lit) Theme.ACCENT.rgb else Theme.TEXT_SECONDARY.rgb
                g.blit(RenderPipelines.GUI_TEXTURED, shape, x, y, 0f, 0f, w, h, w, h, w, h, fade(tint, alpha))
            }
            Kind.OUTLINE -> {
                val side = w - i * 2
                BakedGlass.border(g, x + i, y + i, side, side, Theme.RADIUS_SMALL, 1f, fade(Theme.ACCENT.rgb, alpha))
            }
            Kind.HIDE -> {}
            Kind.HOTBAR -> {
                SkinGlass.panel(g, x, y, w, h, Theme.RADIUS_SMALL, alpha)
                for (slot in 0 until HOTBAR_SLOTS) SkinGlass.draw(g, x + 3 + slot * 20, y + 3, ITEM, ITEM, Glass.CELL, alpha = alpha)
            }
            Kind.OFFHAND_LEFT -> slot(g, x + 3, y + 4, alpha)
            Kind.OFFHAND_RIGHT -> slot(g, x + w - 3 - ITEM, y + 4, alpha)
            Kind.BAR -> {}
            Kind.TAB -> tab(g, x, y, w, h, skin, alpha)
            Kind.CHECKBOX -> checkbox(g, x, y, w, h, skin, alpha)
            Kind.CHECKMARK -> BakedGlass.check(g, x, y + (h - w) / 2, w, fade(Theme.ACCENT.rgb, alpha))
            Kind.SLOT -> when {
                ContainerGlass.paintsCurrent() -> {}
                w == h -> SkinGlass.draw(g, x + 1, y + 1, w - 2, h - 2, Glass.CELL, alpha = alpha)
                else -> return false
            }
        }
        if (skin.danger) BakedGlass.fill(g, x, y, w, h, skin.style.radius, fade(Theme.DANGER.rgb, DANGER_ALPHA * alpha))
        return true
    }

    /** The part-of-a-sprite overload, which the boss bar fills with. True when drawn as glass. */
    fun partSprite(g: GuiGraphicsExtractor, id: Identifier, x: Int, y: Int, w: Int, h: Int, color: Int): Boolean {
        val skin = skinFor(id) ?: return false
        if (skin.kind != Kind.BAR) return false
        val alpha = (color ushr 24) / 255f
        val argb = if (skin.color == 0) SkinGlass.tone(Theme.TRACK.rgb) else skin.color
        BakedGlass.fill(g, x, y, w, h, h / 2f, fade(argb, alpha))
        return true
    }

    private fun skinFor(id: Identifier): Skin? {
        val skin = skins.getOrPut(id) { skinOf(id) ?: NONE }
        return if (skin === NONE || !applies(skin)) null else skin
    }

    private fun applies(skin: Skin): Boolean = when (skin.group) {
        Group.WIDGET -> widgets && inScope()
        Group.CONTAINER -> ContainerGlass.enabled
        Group.TOAST -> toasts
        Group.MENU -> menus
        Group.TOOLTIP -> tooltips
        Group.HOTBAR -> hotbar
        Group.BOSS_BAR -> bossBar
    }

    private fun hover(skin: Skin, x: Int, y: Int, w: Int, h: Int): Float = hovers[rect(x, y, w, h), skin.lit]

    private fun slot(g: GuiGraphicsExtractor, x: Int, y: Int, alpha: Float) {
        SkinGlass.panel(g, x - 3, y - 3, ITEM + 6, ITEM + 6, Theme.RADIUS_SMALL, alpha)
        SkinGlass.draw(g, x, y, ITEM, ITEM, Glass.CELL, alpha = alpha)
    }

    /*
     * Tabs join their panel like the chat tabs: they stop flush at the panel's edge with only their outer corners
     * rounded (the rounded shape runs past the edge under a scissor), the selected one in the panel's own fill with an
     * accent bar on the edge.
     */
    private fun tab(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, skin: Skin, alpha: Float) {
        val o = skin.overlap
        val r = Theme.RADIUS.toInt()
        val vx = if (skin.edge == Edge.LEFT) x + o else x
        val vy = if (skin.edge == Edge.UP) y + o else y
        val vw = if (skin.edge == Edge.LEFT || skin.edge == Edge.RIGHT) w - o else w
        val vh = if (skin.edge == Edge.UP || skin.edge == Edge.DOWN) h - o else h
        val shown = if (skin.selected) 1f else DIM + (1f - DIM) * hover(skin, x, y, w, h) / 2f
        val fill = fade(SkinGlass.tone(Glass.CARD.fill()), alpha * shown)
        g.enableScissor(vx, vy, vx + vw, vy + vh)
        when (skin.edge) {
            Edge.DOWN -> BakedGlass.fill(g, vx, vy, vw, vh + r, Theme.RADIUS, fill)
            Edge.UP -> BakedGlass.fill(g, vx, vy - r, vw, vh + r, Theme.RADIUS, fill)
            Edge.RIGHT -> BakedGlass.fill(g, vx, vy, vw + r, vh, Theme.RADIUS, fill)
            Edge.LEFT -> BakedGlass.fill(g, vx - r, vy, vw + r, vh, Theme.RADIUS, fill)
        }
        g.disableScissor()
        skin.corner?.let { c ->
            val cx = when (skin.edge) {
                Edge.RIGHT -> vx + vw
                Edge.LEFT -> vx - r
                else -> if (c.right) vx + vw - r else vx
            }
            val cy = when (skin.edge) {
                Edge.DOWN -> vy + vh
                Edge.UP -> vy - r
                else -> if (c.bottom) vy + vh - r else vy
            }
            BakedGlass.cornerFill(g, cx, cy, Theme.RADIUS, c, fill)
        }
        if (!skin.selected || !skin.bar) return
        val bar = fade(Theme.ACCENT.rgb, alpha)
        when (skin.edge) {
            Edge.DOWN -> BakedGlass.fill(g, vx + BAR_INSET, vy + vh - 1, vw - BAR_INSET * 2, 2, 1f, bar)
            Edge.UP -> BakedGlass.fill(g, vx + BAR_INSET, vy - 1, vw - BAR_INSET * 2, 2, 1f, bar)
            Edge.RIGHT -> BakedGlass.fill(g, vx + vw - 1, vy + BAR_INSET, 2, vh - BAR_INSET * 2, 1f, bar)
            Edge.LEFT -> BakedGlass.fill(g, vx - 1, vy + BAR_INSET, 2, vh - BAR_INSET * 2, 1f, bar)
        }
    }

    /* The shared GlassCheck; vanilla only highlights a focused checkbox, ours also answers the mouse. */
    private fun checkbox(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, skin: Skin, alpha: Float) {
        val mx = MinecraftWindow.mouseX
        val my = MinecraftWindow.mouseY
        val hover = hovers[rect(x, y, w, h), skin.lit || (mx >= x && mx < x + w && my >= y && my < y + h)]
        val on = checked(rect(x, y, w, h), skin.selected)
        val size = minOf(w, h)
        val r = GlassCheck.RADIUS
        BakedGlass.border(g, x - 1, y - 1, w + 2, h + 2, r + 1f, 1f, fade(GlassCheck.glow(on), alpha))
        BakedGlass.fill(g, x, y, w, h, r, fade(SkinGlass.tone(GlassCheck.base()), alpha))
        BakedGlass.fill(g, x, y, w, h, r, fade(GlassCheck.frost(on, hover), alpha))
        BakedGlass.fill(g, x, y, w, h, r, fade(GlassCheck.accent(on), alpha))
        BakedGlass.border(g, x, y, w, h, r, 1f, fade(GlassCheck.edge(on, hover), alpha))
        if (on <= 0f) return
        g.enableScissor(x, y, x + ceil(size * on).toInt(), y + h)
        BakedGlass.check(g, x, y, size, fade(GlassCheck.mark(), alpha))
        g.disableScissor()
    }

    /* A box first seen on this screen starts settled. Keys are positions, so a scrolling list keeps adding them. */
    private fun checked(key: Long, selected: Boolean): Float {
        val screen = Minecraft.getInstance().currentScreen
        if (screen !== checkScreen || checks.size > MAX_CHECKS) {
            checks.clear()
            checkScreen = screen
        }
        return checks[key, selected]
    }

    /** Vanilla's dark toast text in the theme's colors, readable on glass. */
    fun toastText(color: Int): Int = when {
        !toasts -> color
        color == TOAST_TEXT -> Theme.TEXT_PRIMARY.rgb
        color == TOAST_TITLE -> Theme.ACCENT.rgb
        else -> color
    }

    /* Button Scope "Minecraft Screens": only screens vanilla ships, decided once per screen. */
    private fun inScope(): Boolean {
        if (!minecraftScreensOnly) return true
        val screen = Minecraft.getInstance().currentScreen
        if (screen !== scopeScreen) {
            scopeScreen = screen
            scopeVanilla = screen == null || screen.javaClass.name.startsWith("net.minecraft.")
        }
        return scopeVanilla
    }

    private fun skinOf(id: Identifier): Skin? {
        if (id.namespace != Identifier.DEFAULT_NAMESPACE) return null
        val p = id.path
        val lit = p.endsWith("_highlighted") || p.contains("_selected")
        val dim = p.endsWith("_disabled")
        val selected = p.contains("_selected")
        return when {
            p in TOASTS -> Skin(Group.TOAST, Glass.TOOLTIP)
            p == "tooltip/background" -> Skin(Group.TOOLTIP, Glass.TOOLTIP, inset = TOOLTIP_MARGIN)
            p == "tooltip/frame" -> Skin(Group.TOOLTIP, kind = Kind.HIDE)
            p.startsWith("widget/page_") -> Skin(Group.WIDGET, kind = Kind.ARROW, lit = lit)
            p.startsWith("recipe_book/page_") -> Skin(Group.CONTAINER, kind = Kind.ARROW, lit = lit)
            WIDGET_CELLS.any(p::startsWith) -> Skin(Group.WIDGET, Glass.CELL)
            p.startsWith("widget/checkbox") ->
                Skin(Group.WIDGET, kind = Kind.CHECKBOX, lit = p.endsWith("_highlighted"), selected = selected)
            WIDGET_ICONS.any(p::startsWith) -> Skin(Group.WIDGET, kind = Kind.ICON, lit = lit, dim = dim)
            p == "icon/checkmark" -> Skin(Group.MENU, kind = Kind.CHECKMARK)
            p.startsWith("widget/tab") ->
                Skin(Group.WIDGET, kind = Kind.TAB, lit = p.endsWith("_highlighted"), selected = selected, bar = false)
            WIDGETS.any(p::startsWith) -> Skin(Group.WIDGET, lit = lit, dim = dim)
            p == "popup/background" -> Skin(Group.MENU, Glass.TOOLTIP)
            p in MENU_PANELS -> Skin(Group.MENU, Glass.CARD)
            p == "gamemode_switcher/slot" -> Skin(Group.MENU)
            p == "gamemode_switcher/selection" -> Skin(Group.MENU, kind = Kind.OUTLINE)
            p.startsWith("advancements/tab_") -> tabSkin(Group.MENU, selected, advancementEdge(p), advancementEnd(p))
            p == "hud/hotbar" -> Skin(Group.HOTBAR, kind = Kind.HOTBAR)
            p == "hud/hotbar_selection" -> Skin(Group.HOTBAR, kind = Kind.OUTLINE, inset = 2)
            p == "hud/hotbar_offhand_left" -> Skin(Group.HOTBAR, kind = Kind.OFFHAND_LEFT)
            p == "hud/hotbar_offhand_right" -> Skin(Group.HOTBAR, kind = Kind.OFFHAND_RIGHT)
            p in EFFECTS -> Skin(Group.HOTBAR, dim = p.endsWith("_ambient"))
            p.startsWith("boss_bar/") && !p.startsWith("boss_bar/notched_") -> bar(id, p.endsWith("_progress"))
            p.startsWith("container/creative_inventory/tab_") -> tabSkin(
                Group.CONTAINER, selected, if ("_top_" in p) Edge.DOWN else Edge.UP,
                when (p.last()) { '1' -> false; '7' -> true; else -> null },
            )
            p.startsWith("recipe_book/tab") ->
                Skin(Group.CONTAINER, kind = Kind.TAB, selected = selected, edge = Edge.RIGHT, overlap = if (selected) 3 else 5)
            p == "container/slot" || p == "container/horse/chest_slots" -> Skin(Group.CONTAINER, kind = Kind.SLOT)
            p == "container/bundle/slot_background" -> Skin(Group.TOOLTIP, Glass.CELL)
            p.startsWith("container/anvil/text_field") -> Skin(Group.CONTAINER, dim = dim)
            CELLS.any(p::startsWith) -> Skin(Group.CONTAINER, Glass.CELL, lit = lit, danger = "uncraftable" in p)
            ICONS.any(p::startsWith) -> Skin(Group.CONTAINER, kind = Kind.ICON, lit = lit)
            p.endsWith("/scroller") || p.endsWith("/scroller_disabled") || BUTTONS.any(p::startsWith) ->
                Skin(Group.CONTAINER, lit = lit, dim = dim)
            else -> null
        }
    }

    /* advancements/tab_<side>_<position>[_selected]: left and top tabs start a row, right and bottom ones end it. */
    private fun advancementEnd(p: String): Boolean? = when (p.removePrefix("advancements/tab_").split('_')[1]) {
        "left", "top" -> false
        "right", "bottom" -> true
        else -> null
    }

    /* A tab that sits flush with its panel's first or last corner ([end] null for neither) squares that corner off. */
    private fun tabSkin(group: Group, selected: Boolean, edge: Edge, end: Boolean?): Skin {
        val corner = end?.let {
            when (edge) {
                Edge.DOWN -> if (it) Corner.TOP_RIGHT else Corner.TOP_LEFT
                Edge.UP -> if (it) Corner.BOTTOM_RIGHT else Corner.BOTTOM_LEFT
                Edge.RIGHT -> if (it) Corner.BOTTOM_LEFT else Corner.TOP_LEFT
                Edge.LEFT -> if (it) Corner.BOTTOM_RIGHT else Corner.TOP_RIGHT
            }
        }
        return Skin(group, kind = Kind.TAB, selected = selected, edge = edge, overlap = 4, corner = corner)
    }

    private fun advancementEdge(p: String): Edge = when {
        "_above_" in p -> Edge.DOWN
        "_below_" in p -> Edge.UP
        "_left_" in p -> Edge.RIGHT
        else -> Edge.LEFT
    }

    /* The bar keeps its sprite's color, read from the middle of the sprite so resource packs apply. */
    private fun bar(id: Identifier, progress: Boolean): Skin? {
        if (!progress) return Skin(Group.BOSS_BAR, kind = Kind.BAR)
        val color = Decor.color(id, BAR_WIDTH / 2, BAR_HEIGHT / 2) ?: return null
        return Skin(Group.BOSS_BAR, kind = Kind.BAR, color = color or OPAQUE)
    }

    private fun rect(x: Int, y: Int, w: Int, h: Int): Long =
        ((x.toLong() and 0xFFFF) shl 48) or ((y.toLong() and 0xFFFF) shl 32) or ((w.toLong() and 0xFFFF) shl 16) or
            (h.toLong() and 0xFFFF)

    private val TOASTS = setOf(
        "toast/advancement", "toast/recipe", "toast/system", "toast/tutorial", "toast/now_playing",
        "friends/toast_background",
    )
    private val WIDGETS = listOf("widget/button", "widget/text_field", "widget/slider", "widget/scroller", "friends/button")
    private val WIDGET_CELLS = listOf("widget/scroller_background", "widget/slot_frame")
    private val WIDGET_ICONS = listOf(
        "widget/cross_button", "widget/locked_button", "widget/unlocked_button", "dialog/warning_button",
        "social_interactions/report_button", "social_interactions/mute_button", "social_interactions/unmute_button",
    )
    private val MENU_PANELS = setOf("social_interactions/background", "friends/background", "friends/background_dark")
    private val EFFECTS = setOf(
        "hud/effect_background", "hud/effect_background_ambient",
        "container/inventory/effect_background", "container/inventory/effect_background_ambient",
    )
    private val CELLS = listOf("recipe_book/slot_", "container/stonecutter/recipe", "container/loom/pattern")
    private val ICONS = listOf("recipe_book/button", "recipe_book/filter_", "recipe_book/furnace_filter_")
    private val BUTTONS = listOf("container/enchanting_table/enchantment_slot", "container/beacon/button")

    private const val TOAST_TEXT = 0xFF000000.toInt()
    private const val TOAST_TITLE = 0xFF500050.toInt()
    private const val OPAQUE = 0xFF000000.toInt()
    private const val DIM = 0.55f
    private const val DANGER_ALPHA = 0.25f
    private const val BAR_INSET = 4
    private const val MAX_CHECKS = 64
    private const val ITEM = 16
    private const val HOTBAR_SLOTS = 9
    private const val TOOLTIP_MARGIN = 9
    private const val BAR_WIDTH = 182
    private const val BAR_HEIGHT = 5
}
