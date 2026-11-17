package org.blackaddons.core.ui.screen.actions.fields
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.core.ui.screen.actions.ActionRow
import org.blackaddons.core.ui.screen.actions.actionTr
import java.awt.Color

internal object LocationBar {
    const val HEIGHT = 20f

    fun draw(cx: Float, cy: Float) {
        val label = actionTr("current_location", "Current Location:")
        Render.text(
            label, cx, Render.centerY(cy, HEIGHT, TextStyle.SMALL_MUTED_BOLD),
            TextStyle.SMALL_MUTED_BOLD,
        )
        var x = cx + Render.textWidth(label, Theme.FONT_SMALL, Fonts.bold) + 8f

        x = pill(actionTr("area_value", "Area: %s", LocationUtils.area ?: actionTr("none", "None")), x, cy, Theme.ACCENT)

        val floor = LocationUtils.dungeonFloor?.label ?: actionTr("none", "None")
        if (!LocationUtils.inDungeon) {
            pill(actionTr("dungeon_value", "Dungeon: %s", actionTr("no", "NO")), x, cy, Theme.TEXT_MUTED)
            return
        }

        x = pill(actionTr("floor_value", "Floor: %s", floor), x, cy, DUNGEON_COLOR)
        x = pill(
            actionTr("boss_value", "Boss: %s", actionTr(if (LocationUtils.inBoss) "yes" else "no", if (LocationUtils.inBoss) "YES" else "NO")),
            x, cy, if (LocationUtils.inBoss) BOSS_COLOR else Theme.TEXT_MUTED,
        )
        if (LocationUtils.isF7orM7 || LocationUtils.f7Phase != null) {
            pill(actionTr("phase_value", "Phase: %s", LocationUtils.f7Phase?.let { "P${it.number}" } ?: actionTr("none_upper", "NONE")), x, cy, Theme.BRAND_FROM)
        }
    }

    private fun pill(text: String, x: Float, cy: Float, color: Color): Float {
        ActionRow.tag(text, x, cy + 1f, PILL_H, color)
        return x + ActionRow.tagWidth(text) + PILL_GAP
    }

    private const val PILL_H = 18f
    private const val PILL_GAP = 6f
    private val DUNGEON_COLOR = Color(100, 200, 255)
    private val BOSS_COLOR = Color(255, 120, 120)
}
