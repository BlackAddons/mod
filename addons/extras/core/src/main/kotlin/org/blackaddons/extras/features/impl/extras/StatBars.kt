package org.blackaddons.extras.features.impl.extras

import org.blackaddons.api.Platform
import org.blackaddons.api.event.chat.ActionBarEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.*
import org.blackaddons.api.skyblock.player.ActionBar
import org.blackaddons.api.skyblock.player.ActionBarStats
import org.blackaddons.api.skyblock.player.SkyblockStats
import org.blackaddons.api.skyblock.player.StatGlyphs
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.extras.core.BarRenderer
import java.awt.Color

/**
 * Movable HUD bars for action-bar health, mana and defense, optionally hiding the vanilla rows.
 *
 * Ported from SkyblockAddons (MIT, © Biscuit Development).
 */
object StatBars : Feature(
    description = "Health / mana / defence from the Skyblock action bar as movable HUD bars.",
    name = "Stat Bars",
) {
    private val barWidth by number(
        name = "Bar Width", default = 80.0, min = 40.0, max = 160.0, step = 2.0, unit = "px",
    ).withDescription("Width of the health and mana bars.")

    private val hideActionBar by boolean("Hide Action Bar Stats", default = true)
        .withDescription("Remove from the action bar only the stats you show as HUD elements; everything else (skill xp, timers, un-replaced stats) stays.")

    private val showHealthBar by boolean("Health Bar", default = true).group("Health")
    private val showHealthText by boolean("Health Numbers", default = true)
        .withDescription("Draw the current / max health as a separate movable label.").group("Health")
    private val healthColor by color("Health Color", Color(255, 60, 60, 255))
        .showIf { showHealthBar.value || showHealthText.value }.group("Health")

    private val showEffectiveHp by boolean("Effective HP", default = false)
        .withDescription("Show effective health (health scaled by defence) as a movable label.").group("Health")
    private val ehpColor by color("Effective HP Color", Color(0, 170, 0, 255))
        .showIf { showEffectiveHp.value }.group("Health")

    private val showManaBar by boolean("Mana Bar", default = true).group("Mana")
    private val showManaText by boolean("Mana Numbers", default = true)
        .withDescription("Draw the current / max mana as a separate movable label.").group("Mana")
    private val manaColor by color("Mana Color", Color(50, 130, 255, 255))
        .showIf { showManaBar.value || showManaText.value }.group("Mana")

    private val showManaOverflow by boolean("Mana Overflow", default = true)
        .withDescription("Show overflow mana (ʬ) as a separate label when you have any.").group("Mana")
    private val manaOverflowColor by color("Overflow Color", Color(85, 255, 255, 255))
        .showIf { showManaOverflow.value }.group("Mana")

    private val showDefence by boolean("Defence Text", default = true).group("Defence")
    private val defenceColor by color("Defence Color", Color(90, 200, 110, 255))
        .showIf { showDefence.value }.group("Defence")

    private val showVitalityBar by boolean("Vitality Bar", default = false).group("Vitality")
    private val showVitalityText by boolean("Vitality Numbers", default = true)
        .withDescription("Draw the current / max vitality as a movable label. Vitality only appears while you hold an item that grants it, so it fades out shortly after you stop.").group("Vitality")
    private val vitalityColor by color("Vitality Color", Color(170, 0, 0, 255))
        .showIf { showVitalityBar.value || showVitalityText.value }.group("Vitality")

    private val hideVanillaHealth by boolean("Hide Vanilla Hearts", default = true)
        .withDescription("Hide the vanilla hearts row while on Skyblock.").group("Vanilla HUD")
    private val hideVanillaHunger by boolean("Hide Vanilla Hunger", default = true)
        .withDescription("Hide the vanilla hunger row while on Skyblock.").group("Vanilla HUD")
    private val hideVanillaArmor by boolean("Hide Vanilla Armor", default = true)
        .withDescription("Hide the vanilla armor row while on Skyblock.").group("Vanilla HUD")

    /** Polled each frame by the platform HUD wrappers. */
    // Not in the Rift: we show no health HUD there, and vanilla hearts are the real HP.
    val hideHealthBar: Boolean get() = enabled && hideVanillaHealth.value && Platform.skyblock.onSkyblock && !Platform.skyblock.inRift
    val hideFoodBar: Boolean get() = enabled && hideVanillaHunger.value && Platform.skyblock.onSkyblock
    val hideArmorBar: Boolean get() = enabled && hideVanillaArmor.value && Platform.skyblock.onSkyblock

    private val stats: ActionBarStats get() = SkyblockStats.current

    override fun init() {
        Platform.vanillaHud.hideWhen(VanillaHudPart.HEALTH) { hideHealthBar }
        Platform.vanillaHud.hideWhen(VanillaHudPart.FOOD) { hideFoodBar }
        Platform.vanillaHud.hideWhen(VanillaHudPart.ARMOR) { hideArmorBar }
        register<ActionBarEvent> {
            if (!Platform.skyblock.onSkyblock || !hideActionBar.value) return@register
            // Drop only the sections we replace, the bar also carries timers and cooldowns. Health stays in the Rift.
            val rift = Platform.skyblock.inRift
            val rewritten = ActionBar.rewriteSections(event.replacement ?: event.raw) { section ->
                when {
                    StatGlyphs.hasHeart(section) -> !rift && (showHealthBar.value || showHealthText.value)
                    StatGlyphs.hasMana(section) -> showManaBar.value || showManaText.value
                    StatGlyphs.hasDefense(section) -> showDefence.value
                    StatGlyphs.hasVitality(section) -> showVitalityBar.value || showVitalityText.value
                    else -> false
                }
            }
            when {
                rewritten == null -> {}
                rewritten.isBlank() -> event.cancelled = true
                else -> event.replacement = rewritten
            }
        }
    }

    @Suppress("unused")
    private val healthBarHud = hudElement(
        name = "Health Bar",
        hasText = false,
        enabled = { showHealthBar.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.maxHealth != null && !Platform.skyblock.inRift },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = -49.5f, defaultOffsetY = -34.5f,
    ) { example ->
        val s = if (example) ActionBarStats(health = 900f, maxHealth = 1000f) else stats
        bar(s.health ?: 0f, s.maxHealth ?: 0f, healthColor.value)
    }

    @Suppress("unused")
    private val healthTextHud = hudElement(
        name = "Health Numbers",
        enabled = { showHealthText.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.maxHealth != null && !Platform.skyblock.inRift },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = -65f, defaultOffsetY = -42.5f,
    ) { example ->
        val s = if (example) ActionBarStats(health = 900f, maxHealth = 1000f) else stats
        label("${fmt(s.health ?: 0f)}/${fmt(s.maxHealth ?: 0f)}", healthColor.value)
    }

    @Suppress("unused")
    private val manaBarHud = hudElement(
        name = "Mana Bar",
        hasText = false,
        enabled = { showManaBar.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.maxMana != null },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = 53.5f, defaultOffsetY = -34.5f,
    ) { example ->
        val s = if (example) ActionBarStats(mana = 140f, maxMana = 171f) else stats
        bar(s.mana ?: 0f, s.maxMana ?: 0f, manaColor.value)
    }

    @Suppress("unused")
    private val manaTextHud = hudElement(
        name = "Mana Numbers",
        enabled = { showManaText.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.maxMana != null },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = 72.5f, defaultOffsetY = -42f,
    ) { example ->
        val s = if (example) ActionBarStats(mana = 140f, maxMana = 171f) else stats
        label("${fmt(s.mana ?: 0f)}/${fmt(s.maxMana ?: 0f)}", manaColor.value)
    }

    @Suppress("unused")
    private val manaOverflowHud = hudElement(
        name = "Mana Overflow",
        enabled = { showManaOverflow.value },
        shouldDraw = { Platform.skyblock.onSkyblock && (stats.overflowMana ?: 0f) > 0f },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = 112.5f, defaultOffsetY = -41.5f,
    ) { example ->
        val ov = if (example) 50f else (stats.overflowMana ?: 0f)
        symbolValue(ov, StatGlyphs.OVERFLOW, manaOverflowColor.value)
    }

    @Suppress("unused")
    private val defenceHud = hudElement(
        name = "Defence",
        enabled = { showDefence.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.defense != null },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = -118f, defaultOffsetY = -2f,
    ) { example ->
        val def = if (example) 720f else (stats.defense ?: 0f)
        defence(def)
    }

    @Suppress("unused")
    private val vitalityBarHud = hudElement(
        name = "Vitality Bar",
        hasText = false,
        enabled = { showVitalityBar.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.maxVitality != null },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = 137f, defaultOffsetY = -3f,
    ) { example ->
        val s = if (example) ActionBarStats(vitality = 90f, maxVitality = 122f) else stats
        bar(s.vitality ?: 0f, s.maxVitality ?: 0f, vitalityColor.value)
    }

    @Suppress("unused")
    private val vitalityTextHud = hudElement(
        name = "Vitality Numbers",
        enabled = { showVitalityText.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.maxVitality != null },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = 118f, defaultOffsetY = -11f,
    ) { example ->
        val s = if (example) ActionBarStats(vitality = 90f, maxVitality = 122f) else stats
        labelWithSymbol("${fmt(s.vitality ?: 0f)}/${fmt(s.maxVitality ?: 0f)}", StatGlyphs.VITALITY, vitalityColor.value)
    }

    @Suppress("unused")
    private val effectiveHpHud = hudElement(
        name = "Effective HP",
        enabled = { showEffectiveHp.value },
        shouldDraw = { Platform.skyblock.onSkyblock && stats.health != null && stats.defense != null && !Platform.skyblock.inRift },
        defaultAnchorX = AnchorX.CENTER, defaultAnchorY = AnchorY.BOTTOM,
        defaultOffsetX = -118f, defaultOffsetY = -15f,
    ) { example ->
        val hp = if (example) 1000f else (stats.health ?: 0f)
        val def = if (example) 200f else (stats.defense ?: 0f)
        label(fmt(hp * (def + 100f) / 100f), ehpColor.value)
    }

    /** One bar via [BarRenderer]. Above [max] (absorption, health only) the surplus is gold. */
    private fun bar(current: Float, max: Float, fill: Color): LayoutNode {
        val ratio = if (max > 0f) current / max else 0f
        val fills = when {
            ratio <= 0f -> emptyList()
            ratio <= 1f -> listOf(BarRenderer.Fill(0f, ratio, fill))
            else -> {
                val baseEnd = 1f / ratio // normal part = max/current, the rest is gold
                listOf(BarRenderer.Fill(0f, baseEnd, fill), BarRenderer.Fill(baseEnd, 1f, ABSORPTION))
            }
        }
        return BarRenderer.node(barWidth.value.toFloat(), fill, fills)
    }

    private fun label(text: String, color: Color): LayoutNode = hudTextNode(text, color)

    /** `720❈`, `50ʬ`. */
    private fun symbolValue(
        value: Float, symbol: String, color: Color, symbolScale: Float = 1f,
    ): LayoutNode = labelWithSymbol(fmt(value), symbol, color, symbolScale)

    /** The symbol uses [Fonts.unifont] since the number font doesn't have these dingbats. [symbolScale] shrinks it. */
    private fun labelWithSymbol(
        text: String, symbol: String, color: Color, symbolScale: Float = 1f,
    ): LayoutNode = row(spacing = 1f, align = Align.CENTER) {
        hudText(text, color)
        hudText(symbol, color, size = Theme.FONT_SMALL * symbolScale, font = Fonts.unifont)
    }

    private fun defence(value: Float): LayoutNode =
        symbolValue(value, StatGlyphs.DEFENSE, defenceColor.value, symbolScale = 0.85f)

    private fun fmt(value: Float): String = value.toInt().toString()

    private val ABSORPTION = Color(255, 170, 0)
}
