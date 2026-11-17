package org.blackaddons.core.ui.tab

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.features.impl.visual.Cosmetics
import org.blackaddons.core.ui.tab.AboutTabController.Companion.REVEAL_CLICKS

/**
 * ClickGUI About page: the mod on the left, the people on the right, left-aligned like every other tab. Clicking a name
 * tweens a reveal that [AboutEggs] draws. This class owns the tween and layout.
 */
internal class AboutTabController : TabController {

    private data class Person(val id: String, val name: String, val role: String)

    private val people = listOf(
        Person("dreamcast", "DreamcastERA1", "blackaddons.about.role.team"),
        Person("blackum", "BLACKUM", "blackaddons.about.role.team"),
        Person("atklxve", "AtkLxve", "blackaddons.about.role.team"),
        Person("autismo", "Autismo", "blackaddons.about.role.team"),
        Person("noamm", "Noamm9", "blackaddons.about.role.inspiration"),
    )

    /** Title and credits hide eggs too, so everything is keyed by id. */
    private val ids = people.map { it.id } + TITLE + CREDITS

    private val rows = ids.associateWith { Rect() }

    /** Last drawn position. A flying egg needs its origin. */
    private val homes = mutableMapOf<String, Rect>()

    // Progress 0..1 per name. [AboutEggs] is a pure function of it, so a second click reverses it.
    private val reveal = ids.associateWith { Animation(durationMs = AboutEggs.revealMs(it), easing = Easing.easeInOut) }
    private val awoken = mutableSetOf<String>()

    private val clicks = mutableMapOf<String, Int>()

    // A counted click needs a reaction, or nobody clicks twice. The name lifts and brightens without spoiling the egg.
    private val pulse = ids.associateWith { Animation(durationMs = PULSE_MS) }

    private val brand = listOf(ColorStop(Brand.FROM.rgb, 0f), ColorStop(Brand.TO.rgb, 1f))

    override fun render(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        val left = x + Theme.PADDING_LARGE
        val inner = width - Theme.PADDING_LARGE * 2f
        val columnW = inner * LEFT_FRACTION
        val rightX = left + columnW + Theme.PADDING_LARGE

        val bodyH = height - Theme.PADDING_LARGE * 2f - lineHeight(Theme.FONT_SMALL, Fonts.regular)
        renderIdentity(left, y + Theme.PADDING_LARGE, columnW, bodyH, mouseX, mouseY)
        renderPeople(rightX, y + Theme.PADDING_LARGE, inner - columnW - Theme.PADDING_LARGE, mouseX, mouseY)

        // Pinned to the bottom, so the people list can grow. Credits left, nudge right.
        val bottom = y + height - Theme.PADDING_LARGE - lineHeight(Theme.FONT_SMALL, Fonts.regular)
        val creditsText = I18n.tr("blackaddons.about.credits")
        val creditsW = Render.textWidth(creditsText, Theme.FONT_SMALL, Fonts.regular)
        val creditsH = lineHeight(Theme.FONT_SMALL, Fonts.regular)
        rows.getValue(CREDITS).set(left, bottom, creditsW, creditsH)

        val beat = pulse.getValue(CREDITS).get()
        val creditsHovered = rows.getValue(CREDITS).has(mouseX, mouseY)
        val baseColor = if (creditsHovered) Theme.TEXT_SECONDARY else Theme.TEXT_DISABLED
        val creditsColor = lerpColor(baseColor, Theme.ACCENT, beat)
        Render.text(
            creditsText, left, bottom - beat * PULSE_LIFT,
            Theme.FONT_SMALL, creditsColor, Fonts.regular,
        )

        val hint = I18n.tr("blackaddons.about.hint")
        Render.text(
            hint, x + width - Theme.PADDING_LARGE - Render.textWidth(hint, Theme.FONT_SMALL, Fonts.regular), bottom,
            Theme.FONT_SMALL, Theme.TEXT_DISABLED.withAlpha(HINT_ALPHA), Fonts.regular,
        )

        // Last, so roaming eggs pass over the page. Clipped to the tab, not the screen.
        for (id in ids) {
            val home = homes[id] ?: continue
            AboutEggs.drawFlight(
                id, home.x, home.y,
                x + Theme.PADDING_LARGE, y + Theme.PADDING_LARGE,
                width - Theme.PADDING_LARGE * 2f, bottom - y - Theme.PADDING_LARGE,
                reveal.getValue(id).get(),
            )
        }
    }

    private fun renderIdentity(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        var cy = y
        // Its own lang key, because the headline includes the edition.
        val title = I18n.tr("blackaddons.about.title")
        val beat = pulse.getValue(TITLE).get()
        Render.gradientText(title, x, cy - beat * PULSE_LIFT, Theme.FONT_TITLE, brand, Fonts.bold)
        rows.getValue(TITLE).set(
            x, cy,
            Render.textWidth(title, Theme.FONT_TITLE, Fonts.bold),
            lineHeight(Theme.FONT_TITLE, Fonts.bold),
        )
        cy += advance(Theme.FONT_TITLE, Fonts.bold)

        Render.text(
            I18n.tr("blackaddons.about.version", Platform.blackaddonsVersion), x, cy,
            TextStyle.SMALL_MUTED,
        )
        cy += advance(Theme.FONT_SMALL, Fonts.regular) + Theme.PADDING_LARGE

        Render.rect(x, cy, w * RULE_FRACTION, 1f, Theme.BORDER.withAlpha(90), 0f)
        cy += Theme.PADDING_LARGE

        for (line in Render.wrapText(I18n.tr("blackaddons.about.subtitle"), w, Theme.FONT_BODY, Fonts.regular)) {
            Render.text(line, x, cy, TextStyle.BODY_SECONDARY)
            cy += advance(Theme.FONT_BODY, Fonts.regular)
        }

        // Bottom-anchored and sized as a share of the column, so it can't take over the page.
        val rat = reveal.getValue(TITLE).get()
        if (rat > 0f) {
            val ratW = (w * RAT_FRACTION).coerceAtMost(RAT_MAX_W)
            val ratH = ratW / RAT_RATIO
            AboutEggs.drawRat(x, y + h - ratH, ratW, ratH, rat)
        }
    }

    private fun renderPeople(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        Render.text(
            I18n.tr("blackaddons.about.people"), x, y,
            TextStyle.SMALL_DISABLED_MEDIUM,
        )
        var cy = y + advance(Theme.FONT_SMALL, Fonts.medium) + Theme.PADDING

        for (person in people) {
            val row = rows.getValue(person.id)
            if (AboutEggs.exists(person.id)) row.set(x, cy, w, ROW_H) else row.clear()
            val hovered = row.has(mouseX, mouseY)

            val nameW = drawName(person, x, Render.centerY(cy, ROW_H, Theme.FONT_BODY, Fonts.medium), hovered)
            // Left edge, so an egg drawn next to the name knows where it starts.
            homes.getOrPut(person.id) { Rect() }.set(x, cy + ROW_H / 2f, nameW, 0f)
            val role = I18n.tr(person.role)
            val roleW = Render.textWidth(role, Theme.FONT_SMALL, Fonts.regular)
            val roleX = maxOf(x + nameW + Theme.PADDING, x + w - roleW)
            Render.text(
                role, roleX, Render.centerY(cy, ROW_H, TextStyle.SMALL_DISABLED),
                TextStyle.SMALL_DISABLED,
            )

            val progress = reveal.getValue(person.id).get()
            AboutEggs.drawBelow(person.id, x, cy + ROW_H, progress)
            cy += ROW_H + AboutEggs.extraHeight(person.id, progress)
        }
    }

    /** The pulse belongs to the row, so every egg gets it for free. */
    private fun drawName(person: Person, x: Float, y: Float, hovered: Boolean): Float {
        val beat = pulse.getValue(person.id).get()
        val base = lerpColor(if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY, Theme.ACCENT, beat)
        val progress = reveal.getValue(person.id).get()
        return AboutEggs.draw(person.id, person.name, x, y - beat * PULSE_LIFT, progress, base, brand)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        val hit = ids.firstOrNull { rows.getValue(it).has(mouse.x, mouse.y) } ?: return false
        onClicked(hit)
        return true
    }

    /** [REVEAL_CLICKS] clicks to open, one to close, so a stray click reveals nothing. Closing resets the count. */
    private fun onClicked(id: String) {
        pulse.getValue(id).apply { set(1f); animateTo(0f) }
        if (id == CREDITS) {
            val count = (clicks[id] ?: 0) + 1
            clicks[id] = count
            if (count >= REVEAL_CLICKS) {
                clicks[id] = 0
                Platform.feedback.narrate(if (Cosmetics.toggleNpcCatEars()) "Meow" else "No more meow", interrupt = true)
            }
            return
        }
        if (awoken.remove(id)) {
            clicks[id] = 0
            reveal.getValue(id).animateTo(0f)
            return
        }
        val count = (clicks[id] ?: 0) + 1
        clicks[id] = count
        if (count < REVEAL_CLICKS) return
        awoken.add(id)
        reveal.getValue(id).animateTo(1f)
    }

    private fun lineHeight(size: Float, font: Font): Float = Render.textHeight(size, font)

    /** `size` is an em, not a line height. Stacking by it alone crowds rows. */
    private fun advance(size: Float, font: Font): Float = lineHeight(size, font) + LINE_GAP

    private companion object {
        const val LEFT_FRACTION = 0.52f
        const val RULE_FRACTION = 0.5f
        const val LINE_GAP = 3f
        const val ROW_H = 22f

        const val HINT_ALPHA = 90

        const val REVEAL_CLICKS = 3

        const val TITLE = "title"

        /** Triple-click toggles [Cosmetics.npcCatEars] and says so. */
        const val CREDITS = "credits"

        /** Share of the column, capped, at the image's ratio. */
        const val RAT_FRACTION = 0.55f
        const val RAT_MAX_W = 190f
        const val RAT_RATIO = 992f / 688f

        /** Click acknowledgement: short, lifts the name a couple of pixels. */
        const val PULSE_MS = 220L
        const val PULSE_LIFT = 2f
    }
}
