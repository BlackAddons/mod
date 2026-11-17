package org.blackaddons.extras.features.impl.extras

import org.blackaddons.api.Platform
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ActionBarEvent
import org.blackaddons.api.event.skyblock.SkillXpEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.hud.hudText
import org.blackaddons.api.skyblock.player.ActionBar
import org.blackaddons.api.skyblock.player.SkillGain
import org.blackaddons.api.skyblock.profile.Skill
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.Spacer
import org.blackaddons.api.ui.layout.faded
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.extras.core.iconItem
import org.blackaddons.extras.features.impl.extras.SkillDisplay.FADE_OUT_MS
import java.awt.Color
import java.util.Locale

/** Skill-xp pop-up as a movable HUD element, fading out after the last gain like SkyblockAddons' SKILL_DISPLAY. */
object SkillDisplay : Feature(
    description = "Skyblock skill xp as a movable HUD element (icon + name + percent).",
    name = "Skill Display",
) {
    private val showIcon by boolean("Skill Icon", default = true)
    private val iconSize by number(
        name = "Icon Size", default = 18.0, min = 8.0, max = 40.0, step = 1.0, unit = "px",
    ).withDescription("Size of the skill's item icon.").showIf { showIcon.value }
    private val showPercent by boolean("Show Percent", default = true)
    private val color by color("Color", Color(255, 255, 255, 255))
    private val fadeSeconds by number(
        name = "Fade Time", default = 4.0, min = 1.0, max = 20.0, step = 1.0, unit = "s",
    ).withDescription("How long the display lingers after the last xp gain.")
    private val hideFromActionBar by boolean("Hide From Action Bar", default = true)
        .withDescription("Remove the skill-xp section from the action bar (it's shown here instead).")

    // Client thread only, @Volatile publishes the reference.
    @Volatile
    private var current: SkillGain? = null
    @Volatile
    private var lastUpdateMs = 0L

    private const val FADE_OUT_MS = 800L

    override fun init() {
        register<SkillXpEvent> {
            current = event.gain
            lastUpdateMs = System.currentTimeMillis()
        }
        register<ActionBarEvent> {
            if (!Platform.skyblock.onSkyblock) return@register
            if (hideFromActionBar.value) {
                // Plays nice with other features' rewrites.
                val rewritten = ActionBar.rewriteSections(event.replacement ?: event.raw, ActionBar::isSkillSection)
                when {
                    rewritten == null -> {}
                    rewritten.isBlank() -> event.cancelled = true
                    else -> event.replacement = rewritten
                }
            }
        }
        register<WorldChangeEvent> { current = null }
    }

    /** 1 while fresh, easing to 0 over [FADE_OUT_MS]. */
    private fun displayAlpha(): Float {
        if (current == null) return 0f
        val elapsed = System.currentTimeMillis() - lastUpdateMs
        val total = (fadeSeconds.value * 1000.0).toLong()
        return when {
            elapsed >= total -> 0f
            elapsed >= total - FADE_OUT_MS -> ((total - elapsed).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    @Suppress("unused")
    private val skillHud = hudElement(
        name = "Skill Display",
        shouldDraw = { Platform.skyblock.onSkyblock && displayAlpha() > 0f },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = 6.5f, defaultOffsetY = -83f,
    ) { example ->
        if (example) skill(SkillGain(Skill.COMBAT, 12f, 52.3f), 1f)
        else current?.let { skill(it, displayAlpha()) } ?: Spacer()
    }

    /** Icon and label centered in the row. No shadow, it doubles anti-aliased text and looks bad. */
    private fun skill(s: SkillGain, alpha: Float): LayoutNode = row(spacing = 4f, align = Align.CENTER) {
        if (showIcon.value) {
            val size = iconSize.value.toFloat()
            cell(size, size) { x, y -> Platform.renderer.item(s.skill.iconItem, x, y, size, size) }
        }
        hudText(label(s), color.value)
    }.faded(alpha)

    private fun label(s: SkillGain): String {
        val name = s.skill.displayName
        return when {
            !showPercent.value -> name
            // Maxed: show accumulated xp instead, or "MAX".
            s.maxed -> s.currentXp
                ?.let { "$name (${"%,d".format(Locale.US, it.toLong())})" }
                ?: "$name ${I18n.trOr("blackaddons.skill.max", "MAX")}"
            else -> "$name (${s.percent.formatDecimals(1)}%)"
        }
    }
}
