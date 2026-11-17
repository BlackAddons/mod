package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.cosmetics.NickStyle
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.CosmeticName
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.util.I18n
import java.awt.Color
import java.util.Locale
import kotlin.math.roundToInt

/** Hand-drawn controls for the cosmetic editor (pill tab, slider, speed, empty state). Should be toolkit widgets. */

internal const val SPEED_MIN = 0.1f
internal const val SPEED_MAX = 5f

internal fun labelAdvance(): Float = Render.textHeight(Theme.FONT_SMALL, Fonts.regular) + 3f

internal fun speedFrac(v: Float): Float = ((v - SPEED_MIN) / (SPEED_MAX - SPEED_MIN)).coerceIn(0f, 1f)
internal fun speedValue(frac: Float): Float = snapToStep(SPEED_MIN + frac.coerceIn(0f, 1f) * (SPEED_MAX - SPEED_MIN))

/** Raw drags stored noise like 0.9975. */
internal const val SLIDER_STEP = 0.05f

internal fun snapToStep(v: Float): Float = (v / SLIDER_STEP).roundToInt() * SLIDER_STEP
internal fun fracFromMouse(rect: Rect, mx: Float): Float = ((mx - rect.x) / rect.w).coerceIn(0f, 1f)

/** A [labelStyle] that isn't plain draws the label in that style, so a style toggle shows what it does. */
internal fun drawPill(
    rect: Rect, label: String, selected: Boolean, mouseX: Float, mouseY: Float,
    labelStyle: NickStyle = NickStyle.NONE,
) {
    val hover = rect.has(mouseX, mouseY)
    val bg = when {
        selected -> Theme.ACCENT
        hover -> Theme.SURFACE_HOVER
        else -> Theme.SURFACE.withAlpha(80)
    }
    Render.rect(rect, bg, Theme.RADIUS_SMALL)
    val color = if (selected) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY
    val textY = Render.centerY(rect.y, rect.h, Theme.FONT_SMALL, Fonts.medium)
    if (labelStyle.isPlain) {
        val tw = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
        Render.text(label, centered(rect.x, rect.w, tw), textY, Theme.FONT_SMALL, color, Fonts.medium)
        return
    }
    val styled = Cosmetic(style = labelStyle)
    val tw = CosmeticName.width(styled, label, Theme.FONT_SMALL, Fonts.medium)
    CosmeticName.draw(styled, label, centered(rect.x, rect.w, tw), textY, Theme.FONT_SMALL, Fonts.medium, color)
}

internal fun drawSlider(rect: Rect, frac: Float, enabled: Boolean) {
    val trackY = centered(rect.y, rect.h, 4f)
    val f = frac.coerceIn(0f, 1f)
    Render.rect(rect.x, trackY, rect.w, 4f, Color(50, 55, 68), 2f)
    Render.rect(rect.x, trackY, rect.w * f, 4f, if (enabled) Theme.ACCENT else Theme.ACCENT.withAlpha(90), 2f)
    Render.circle(rect.x + rect.w * f, rect.y + rect.h / 2f, 5f, if (enabled) Color.WHITE else Color(200, 200, 200))
}

internal fun renderStatusMessage(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    message: String,
    retry: Button,
    showRetry: Boolean,
    mouseX: Float,
    mouseY: Float,
) {
    val tw = Render.textWidth(message, Theme.FONT_BODY, Fonts.medium)
    val msgY = y + h / 2f - Theme.FONT_BODY
    Render.text(message, centered(x, w, tw), msgY, TextStyle.BODY_SECONDARY_MEDIUM)

    if (showRetry) {
        retry.render(centered(x, w, RETRY_W), msgY + Theme.FONT_BODY + Theme.PADDING_LARGE, RETRY_W, RETRY_H, mouseX, mouseY)
    } else {
        retry.clear()
    }
}

private const val RETRY_W = 80f
private const val RETRY_H = 24f

internal fun renderSpeed(x: Float, startY: Float, w: Float, speed: Float, sliderRect: Rect): Float {
    Render.text(I18n.trOr("blackaddons.cosmetic_editor.speed", "Speed"), x, startY, TextStyle.SMALL_SECONDARY)
    Render.text("%.2f×".format(Locale.ROOT, speed), x + w - 40f, startY, TextStyle.SMALL_MUTED)
    sliderRect.set(x, startY + labelAdvance(), w, 8f)
    drawSlider(sliderRect, speedFrac(speed), true)
    return startY + labelAdvance() + 20f
}
