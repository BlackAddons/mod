package org.blackaddons.core.features.impl.dungeons.leapmenu

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.gui.ContainerOverlay
import org.blackaddons.api.input.InputType
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonPlayer
import org.blackaddons.api.skyblock.dungeon.SpiritLeapMenu
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.render.CosmeticName
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.cosmetics.PlayerCosmetics
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.map.MapPlayer
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.features.impl.dungeons.map.MapRenderer
import java.awt.Color
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

private val tr = I18n.scope("blackaddons.leap_menu")

internal object LeapMenuOverlay : ContainerOverlay {

    private class Card(val player: DungeonPlayer, val uuid: UUID?, val location: String?)

    private var cards: List<Card?> = emptyList()
    private var hovered: Int? = null
    private var armed = false
    private var pending: String? = null
    private val hover = List(LeapOrder.CORNERS) { Animation(durationMs = Theme.ANIM_HOVER.toLong()) }

    override fun appliesTo(title: String): Boolean =
        LeapMenu.enabled && LocationUtils.inDungeon && SpiritLeapMenu.isMenu(title)

    override fun keepsCursor(title: String): Boolean = false

    fun tick() {
        if (Platform.container.title()?.let(::appliesTo) != true) return reset()
        val heads = SpiritLeapMenu.heads()
        val team = Dungeon.team.others
        /* Without a tab list (P3Sim), the heads in the menu are the team. */
        val strangers = heads.keys
            .filter { name -> team.none { it.name.equalsIgnoreCase(name) } && !name.equalsIgnoreCase(Platform.player.name) }
            .map { DungeonPlayer(it) }
        cards = LeapOrder.arrange(team + strangers, LeapMenu.sorting.value, LeapMenu.customOrder.value).map { player ->
            player ?: return@map null
            val uuid = Platform.world.playerUuid(player.name)
            val location = if (LeapMenu.showLocation.value) TeammateLocation.of(player.name, uuid) else null
            Card(player, uuid, location)
        }
        pending?.let(::click)
    }

    fun reset() {
        cards = emptyList()
        hovered = null
        armed = false
        pending = null
    }

    override fun renderUi(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        val w = metrics.width.toFloat()
        val h = metrics.height.toFloat()
        Render.rect(0, 0, w, h, Theme.OVERLAY_DIM.withAlpha(DIM_ALPHA))
        LeapMenu.leapingTo?.let {
            hovered = null
            Render.textCentered(tr("leaping_to", "Leaping to %s", it), w / 2, h / 2, TextStyle.TITLE_PRIMARY_MEDIUM)
            return
        }
        if (cards.all { it == null }) {
            hovered = null
            Render.textCentered(tr("no_teammates", "No teammates found"), w / 2, h / 2, TextStyle.BODY_MUTED)
            return
        }
        if (mapLeapActive()) drawMap(w, h, mouseX, mouseY) else drawCards(w, h, mouseX, mouseY)
    }

    override fun render(ctx: Any, metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {}

    override fun mouseClicked(metrics: ContainerMetrics, mouse: MouseButtonInput): Boolean {
        boundTarget(InputType.MOUSE, mouse.button)?.let {
            leap(it)
            return true
        }
        if (LeapMenu.leftClickOnly.value && !mouse.isLeft) return true
        if (LeapMenu.onRelease.value) armed = true else leap(hovered)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput) {
        if (!armed) return
        armed = false
        leap(hovered)
    }

    override fun keyPressed(metrics: ContainerMetrics, key: KeyInput): Boolean {
        val target = boundTarget(InputType.KEYBOARD, key.code) ?: return false
        leap(target)
        return true
    }

    /* Card index for a bound input. -1 when bound but nobody fits, null when unbound. */
    private fun boundTarget(type: InputType, code: Int): Int? = when (LeapMenu.keybindMode.value) {
        LeapMenu.KEYS_CORNERS -> LeapMenu.cornerKeys.indexOfFirst { it.matches(type, code) }.takeIf { it >= 0 }
        LeapMenu.KEYS_CLASS -> LeapMenu.classKeys.entries.firstOrNull { it.value.matches(type, code) }?.key
            ?.let { dungeonClass -> cards.indexOfFirst { it?.player?.dungeonClass == dungeonClass } }
        else -> null
    }

    private fun leap(index: Int?) {
        if (LeapMenu.leapingTo != null) return
        val card = index?.let(cards::getOrNull) ?: return
        if (card.player.isDead) {
            Platform.chat.local(ChatMessage.build {
                brand()
                append(tr("dead_message", "%s is dead.", card.player.name), Brand.FAIL)
            })
            return
        }
        click(card.player.name)
    }

    private fun click(name: String) {
        pending = name
        val slot = SpiritLeapMenu.headSlot(name) ?: return
        pending = null
        Platform.container.clickSlot(slot, button = 0, quickMove = false)
    }

    private fun drawCards(w: Float, h: Float, mouseX: Int, mouseY: Int) {
        val cx = w / 2
        val cy = h / 2
        val quadrant = (if (mouseY < cy) 0 else 2) + (if (mouseX < cx) 0 else 1)
        hovered = quadrant.takeIf { cards.getOrNull(it) != null }

        val viewH = if (LeapMenu.showView.value) (CARD_W - PAD * 2) * Platform.window.scaledHeight / Platform.window.scaledWidth else 0f
        val cardH = (if (viewH > 0f) viewH + PAD else 0f) + INFO_H
        val fit = minOf((w - MARGIN * 2) / (CARD_W * 2 + GAP), (h - MARGIN * 2) / (cardH * 2 + GAP))
        val scale = minOf(LeapMenu.scale.value.toFloat(), fit)
        Render.push()
        Render.translate(cx, cy)
        Render.scale(scale, scale)
        cards.forEachIndexed { i, card ->
            hover[i].animateTo(if (i == hovered) 1f else 0f)
            card ?: return@forEachIndexed
            val x = if (i % 2 == 0) -GAP / 2 - CARD_W else GAP / 2
            val y = if (i < 2) -GAP / 2 - cardH else GAP / 2
            drawCard(card, x, y, cardH, viewH, hover[i].get(), i == hovered)
        }
        Render.pop()
    }

    private fun drawCard(card: Card, x: Float, y: Float, h: Float, viewH: Float, hoverT: Float, isHovered: Boolean) {
        val player = card.player
        var fill = if (LeapMenu.classColored.value) lerpColor(cardFill, player.dungeonClass.color, CLASS_TINT) else cardFill
        if (LeapMenu.tintDead.value && player.isDead) fill = lerpColor(fill, Theme.DANGER, DEAD_TINT)
        Render.rect(x, y, CARD_W, h, lerpColor(fill, Theme.SURFACE_HOVER, hoverT), Theme.RADIUS_LARGE)
        val opener = LeapMenu.showDoorOpener.value && DungeonTracker.lastDoorOpener?.name.equalsIgnoreCase(player.name)
        val border = lerpColor(if (opener) Theme.SUCCESS else Theme.BORDER, Theme.ACCENT, hoverT)
        Render.hollowRect(x, y, CARD_W, h, OUTLINE, border, Theme.RADIUS_LARGE)

        if (viewH > 0f) drawView(card, x + PAD, y + PAD, CARD_W - PAD * 2, viewH, isHovered)
        drawInfo(card, x, y + h - INFO_H)
    }

    private fun drawView(card: Card, x: Float, y: Float, w: Float, h: Float, isHovered: Boolean) {
        val fps = if (isHovered) LeapMenu.viewFps.value.toInt() else IDLE_VIEW_FPS
        if (card.uuid?.let { Platform.camera.drawEntityView(it, x, y, w, h, fps, radius = Theme.RADIUS) } == true) return
        Render.rect(x, y, w, h, Theme.SURFACE, Theme.RADIUS)
        Render.textCentered(tr("no_view", "No view"), x + w / 2, Render.centerY(y, h, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED)
    }

    private fun drawInfo(card: Card, x: Float, y: Float) {
        val player = card.player
        val classColor = player.dungeonClass.color
        var textX = x + PAD
        if (LeapMenu.showHead.value) {
            val headY = y + (INFO_H - HEAD) / 2
            Render.rect(textX - HEAD_BORDER, headY - HEAD_BORDER, HEAD + HEAD_BORDER * 2, HEAD + HEAD_BORDER * 2, classColor, Theme.RADIUS_SMALL + HEAD_BORDER)
            Render.playerFace(player.name, textX, headY, HEAD, HEAD, radius = Theme.RADIUS_SMALL)
            textX += HEAD + PAD
        }
        val textW = x + CARD_W - PAD - textX
        val className = player.dungeonClass.takeIf { it != DungeonClass.EMPTY }?.displayName
        val classLabel = if (LeapMenu.label.value == LeapMenu.LABEL_CLASS) className else null
        val secondary = when {
            player.isDead -> tr("dead", "DEAD")
            LeapMenu.label.value == LeapMenu.LABEL_BOTH -> className
            else -> null
        }
        val location = card.location
        val twoLines = secondary != null || location != null
        val primary = TextStyle.BODY_PRIMARY_MEDIUM
        val primaryY = if (twoLines) Render.centerY(y + ROW_TOP, ROW_H, primary) else Render.centerY(y, INFO_H, primary)
        if (classLabel != null) {
            Render.text(classLabel, textX, primaryY, primary.size, classColor, primary.font, textW)
        } else {
            val cosmetic = CosmeticName.ellipsize(
                PlayerCosmetics.nameFor(player.name, card.uuid), player.name, textW, primary.size, primary.font,
            )
            CosmeticName.draw(cosmetic, player.name, textX, primaryY, primary.size, primary.font, primary.color)
        }
        if (!twoLines) return

        val small = TextStyle.SMALL_SECONDARY_MEDIUM
        val lineY = Render.centerY(y + ROW_TOP + ROW_H, ROW_H, small)
        var used = 0f
        if (secondary != null) {
            used = Render.text(secondary, textX, lineY, small.size, if (player.isDead) Theme.DANGER else classColor, small.font, textW) + PAD
        }
        if (location != null && textW - used > 0f) {
            Render.textRight(location, x + CARD_W - PAD, lineY, TextStyle.SMALL_MUTED, maxWidth = textW - used)
        }
    }

    private fun mapLeapActive(): Boolean =
        LeapMenu.mapLeap.value && MapState.calibrated && !LocationUtils.inBoss &&
            (!LeapMenu.mapLeapAfterBlood.value || Dungeon.run.bloodOpenTime != null)

    private fun drawMap(w: Float, h: Float, mouseX: Int, mouseY: Int) {
        val mapSize = MapRenderer.MAP_SIZE
        val fit = minOf(w - MARGIN * 2, h - MARGIN * 2) / (mapSize + MAP_PAD * 2)
        val scale = minOf(LeapMenu.mapLeapScale.value.toFloat(), fit)
        val ox = (w - mapSize * scale) / 2
        val oy = (h - mapSize * scale) / 2
        val mapBox = mapSize + MAP_PAD * 2
        Render.push()
        Render.translate(ox, oy)
        Render.scale(scale, scale)
        Render.rect(-MAP_PAD, -MAP_PAD, mapBox, mapBox, cardFill, Theme.RADIUS_LARGE)
        Render.hollowRect(-MAP_PAD, -MAP_PAD, mapBox, mapBox, OUTLINE, Theme.BORDER, Theme.RADIUS_LARGE)
        MapRenderer.drawLayers(0f, 0f)
        Render.pop()

        val mx = (mouseX - ox) / scale - MapRenderer.centerOffsetX
        val my = (mouseY - oy) / scale - MapRenderer.centerOffsetY
        var best: Pair<Int, MapPlayer>? = null
        var bestDistance = Float.MAX_VALUE
        cards.forEachIndexed { i, card ->
            if (card == null || card.player.isDead) return@forEachIndexed
            val marker = MapState.player(card.player.name) ?: return@forEachIndexed
            if (abs(marker.mapX - mx) > MARKER_HIT || abs(marker.mapZ - my) > MARKER_HIT) return@forEachIndexed
            val distance = hypot(marker.mapX - mx, marker.mapZ - my)
            if (distance < bestDistance) {
                best = i to marker
                bestDistance = distance
            }
        }
        hovered = best?.first
        best?.second?.let { marker ->
            val side = MARKER_HIT * 2 * scale
            Render.hollowRect(
                ox + (marker.mapX + MapRenderer.centerOffsetX - MARKER_HIT) * scale,
                oy + (marker.mapZ + MapRenderer.centerOffsetY - MARKER_HIT) * scale,
                side, side, OUTLINE, Theme.ACCENT, Theme.RADIUS_SMALL,
            )
        }
    }

    private const val CARD_W = 200f
    private const val INFO_H = 40f
    private const val ROW_TOP = 4f
    private const val ROW_H = 16f
    private const val HEAD = 28f
    private const val HEAD_BORDER = 1f
    private const val PAD = 6f
    private const val GAP = 16f
    private const val MARGIN = 8f
    private const val OUTLINE = 1f
    private const val DIM_ALPHA = 110
    private const val CARD_ALPHA = 235
    private const val CLASS_TINT = 0.35f
    private const val DEAD_TINT = 0.25f
    private const val IDLE_VIEW_FPS = 30
    private const val MAP_PAD = 4f
    private const val MARKER_HIT = 7f

    private val cardFill: Color get() = Theme.BACKGROUND.withAlpha(CARD_ALPHA)
}
