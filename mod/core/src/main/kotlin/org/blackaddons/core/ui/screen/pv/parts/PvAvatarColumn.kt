package org.blackaddons.core.ui.screen.pv.parts
import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.profile.PlayerProfiles
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.render.CosmeticName
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.concurrent.LatestOnly
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.gateway.Presence
import org.blackaddons.core.features.impl.settings.Connection
import org.blackaddons.core.profile.PlayerStatus
import org.blackaddons.core.profile.PlayerStatusSource
import org.blackaddons.core.profile.SkinTextures
import org.blackaddons.core.profile.dashless
import org.blackaddons.core.ui.screen.pv.PvFormat
import org.blackaddons.core.ui.screen.pv.SkyblockLevelTier
import java.awt.Color

private val tr = I18n.scope("blackaddons.pv")

/**
 * Overview left column: nickname with Skyblock level, the 3D model and current area. It's the only interactive part of
 * the tab, so it owns the turntable and the area lookup state.
 */
internal class PvAvatarColumn : InputTarget {

    /** Current area, on demand, never cached (stale in seconds). [areaRequested] tells "not asked" from "loading". */
    @Volatile
    private var status: PlayerStatus? = null
    private var areaRequested = false
    private val areaButton = Button(tr("load", "Load")) { loadArea() }
    private var currentUuid: String? = null
    private val statusLookups = LatestOnly()

    private val turntable = PvTurntable()

    /** Forgets the previous player's area lookup. Called when a new profile starts to load. */
    fun reset() {
        statusLookups.cancel()
        status = null
        areaRequested = false
        hide()
    }

    /** Drops the hitboxes while the tab isn't on screen, so nothing behind it stays clickable. */
    fun hide() {
        areaButton.clear()
        turntable.hide()
    }

    fun node(
        profiles: PlayerProfiles,
        textures: SkinTextures?,
        level: Int,
        w: Float,
        height: Float,
        chipH: Float,
        mouseX: Float,
        mouseY: Float,
        deltaTicks: Float,
    ): LayoutNode {
        currentUuid = profiles.uuid.dashless

        val nickH = Render.textHeight(Theme.FONT_BODY, Fonts.bold)
        val boxH = (height - nickH - chipH - Theme.PADDING * 2f).coerceAtLeast(120f)
        return column(spacing = Theme.PADDING, width = w) {
            cell(w, nickH) { cx, cy -> drawNickname(cx, cy, w, level, profiles) }
            cell(w, boxH) { cx, cy ->
                turntable.draw(cx, cy, w, boxH, profiles, textures, deltaTicks)
                drawSizeBadge(cx, cy, w, profiles.uuid.dashless)
            }
            cell(w, chipH) { cx, cy -> drawArea(cx, cy, chipH, mouseX, mouseY) }
        }
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean =
        areaButton.mouseClicked(mouse) || turntable.mouseClicked(mouse)

    override fun mouseReleased(mouse: MouseButtonInput): Boolean =
        turntable.mouseReleased(mouse)

    override fun mouseDragged(drag: MouseDragInput): Boolean =
        turntable.mouseDragged(drag)

    private fun loadArea() {
        val uuid = currentUuid ?: return
        areaRequested = true
        status = null // clears a previous failure, so a retry shows "..." instead of staying "[Retry]"
        OffThread.run(
            name = "pv-status",
            work = { PlayerStatusSource.fetch(uuid) },
            onFailure = { PlayerStatus.Unavailable },
            onResult = statusLookups.next { status = it },
        )
    }

    /** `[267] Name`: level in its tier color, then the name painted by the player's own cosmetic. */
    private fun drawNickname(x: Float, y: Float, w: Float, level: Int, profiles: PlayerProfiles) {
        var cx = x
        cx += part("[", cx, y, Theme.TEXT_MUTED)
        cx += part(level.toString(), cx, y, SkyblockLevelTier.color(level))
        cx += part("] ", cx, y, Theme.TEXT_MUTED)

        // Same online mark as in the world (see [Presence]). A long name gives up room for it.
        if (Connection.onlineMarkers) Presence.watch(profiles.uuid)
        val online = Connection.onlineMarkers && Presence.isOnline(profiles.uuid)
        val mark = if (online) MARK_GAP + Render.textWidth(ONLINE_MARK, Theme.FONT_BODY, Fonts.bold) else 0f

        val cosmetic = CosmeticName.ellipsize(
            Platform.cosmetics.nameFor(profiles.uuid), profiles.name, x + w - cx - mark, Theme.FONT_BODY, Fonts.bold,
        )
        cx += CosmeticName.draw(cosmetic, profiles.name, cx, y, Theme.FONT_BODY, Fonts.bold, Theme.TEXT_PRIMARY)
        if (online) Render.text(ONLINE_MARK, cx + MARK_GAP, y, Theme.FONT_BODY, ONLINE_COLOR, Fonts.bold)
    }

    private fun part(text: String, x: Float, y: Float, color: Color): Float {
        Render.text(text, x, y, Theme.FONT_BODY, color, Fonts.bold)
        return Render.textWidth(text, Theme.FONT_BODY, Fonts.bold)
    }

    /** Labels a non-vanilla model scale. The avatar is framed to fit, so without this it would look normal. */
    private fun drawSizeBadge(x: Float, y: Float, w: Float, uuid: String) {
        val size = Platform.cosmetics.scaleFor(Uuids.parse(uuid)) ?: return
        // Compared as printed, so a value very close to 1 shows no badge.
        val axes = listOf(size.x, size.y, size.z).map(PvFormat::axis).distinct()
        if (axes == listOf("1")) return
        val label = axes.singleOrNull()?.let { "×$it" }
            ?: listOf(size.x, size.y, size.z).joinToString(" × ", transform = PvFormat::axis)

        val padding = Theme.PADDING_SMALL
        val bw = Render.textWidth(label, Theme.FONT_SMALL, Fonts.regular) + padding * 2f
        val bh = Render.textHeight(Theme.FONT_SMALL, Fonts.regular) + padding
        val bx = x + w - bw - padding
        val by = y + padding
        Render.rect(bx, by, bw, bh, Theme.SURFACE, Theme.RADIUS_SMALL)
        Render.text(
            label, bx + padding, Render.centerY(by, bh, TextStyle.SMALL_SECONDARY),
            TextStyle.SMALL_SECONDARY,
        )
    }

    /** Area is a second API call, so it waits behind a "Load" button. */
    private fun drawArea(x: Float, y: Float, h: Float, mouseX: Float, mouseY: Float) {
        val label = tr("area", "Area") + ": "
        val ty = Render.centerY(y, h, Theme.FONT_SMALL, Fonts.regular)
        Render.text(label, x, ty, TextStyle.SMALL_MUTED)
        val vx = x + Render.textWidth(label, Theme.FONT_SMALL, Fonts.regular)

        val state = status
        // A failed lookup becomes a button again. Other states are final until reload.
        if (!areaRequested || state is PlayerStatus.Unavailable) {
            val retry = state is PlayerStatus.Unavailable
            areaButton.label =
                if (retry) tr("retry", "Retry") else tr("load", "Load")
            areaButton.render(vx, y, areaButton.naturalWidth(), h, mouseX, mouseY)
            return
        }
        areaButton.clear()

        when (state) {
            null -> Render.text("...", vx, ty, TextStyle.SMALL_MUTED)
            is PlayerStatus.Online ->
                Render.text(state.area, vx, ty, TextStyle.SMALL_SECONDARY)
            // Hypixel reports offline and status-API-off the same way (NEU does the same).
            else -> Render.text(
                tr("offline", "Offline"), vx, ty,
                TextStyle.SMALL_MUTED,
            )
        }
    }

    private companion object {
        const val ONLINE_MARK = "●"
        const val MARK_GAP = 4f
        val ONLINE_COLOR = Color(0x7CE0A2)
    }
}
