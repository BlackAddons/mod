package org.blackaddons.api.skyblock.player

import org.blackaddons.api.skyblock.profile.Skill
import org.blackaddons.api.util.stripFormatting
import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale

/**
 * Stats from one SkyBlock action bar. Null means the bar did not show it (keep the value you had), except [defense],
 * which is 0 when Hypixel leaves it out.
 */
data class ActionBarStats(
    val health: Float? = null,
    val maxHealth: Float? = null,
    val mana: Float? = null,
    val maxMana: Float? = null,
    val overflowMana: Float? = null,
    val defense: Float? = null,
    /** Hypixel's vitality, shown only with certain items held, and only in its custom font. */
    val vitality: Float? = null,
    val maxVitality: Float? = null,
    /** The healing wand's tick next to health. */
    val wandHealing: Float? = null,
) {
    /** These values, with every stat [next] shows taking the place of the old one. */
    fun merge(next: ActionBarStats) = ActionBarStats(
        health = next.health ?: health,
        maxHealth = next.maxHealth ?: maxHealth,
        mana = next.mana ?: mana,
        maxMana = next.maxMana ?: maxMana,
        overflowMana = next.overflowMana ?: overflowMana,
        defense = next.defense ?: defense,
        vitality = next.vitality ?: vitality,
        maxVitality = next.maxVitality ?: maxVitality,
        wandHealing = next.wandHealing,
    )
}

/**
 * A skill xp gain from the action bar, as `+10.9 Combat (313,937.1/600,000)` or `+5 Combat (52%)`. [percent] is
 * 0..100. A total of 0 (`+60 Runecrafting (118,084/0)`) means [maxed]: [percent] is 100 and [currentXp] holds the
 * growing xp.
 */
data class SkillGain(
    val skill: Skill,
    val gained: Float,
    val percent: Float,
    val maxed: Boolean = false,
    val currentXp: Float? = null,
)

/**
 * Reads a SkyBlock action bar, whose sections are three or more spaces apart:
 * ```
 * §c1390/1390❤     §a720§a❈ Defense     §b183/171✎ Mana§r
 * §61181/1161❤+§c20▆     §a593§a❈ Defense     §b501/550✎ -10ʬ
 * ```
 * Glyphs come as legacy symbols or Hypixel's custom font; both are accepted ([StatGlyphs]). Pass the bar with its
 * `§` codes. For the live values use [SkyblockStats] instead of parsing every bar yourself.
 *
 * Ported from SkyblockAddons (MIT, © Biscuit Development).
 */
object ActionBar {

    // Color-stripped sections. Glyph classes match legacy and custom-font symbols; wand bar chars are the same in both.
    private val HEALTH = Regex("""(?<health>[0-9,.]+)/(?<maxHealth>[0-9,.]+)${StatGlyphs.HEART_CLASS}(?<wand>\+(?<wandHeal>[0-9,.]+)[▆▅▄▃▂▁])?""")
    // Overflow must come before the empty branch: alternation is ordered.
    private val MANA = Regex("""(?<num>[0-9,.]+)/(?<den>[0-9,.]+)${StatGlyphs.MANA_CLASS}(?: (?<overflow>-?[0-9,.]+)${StatGlyphs.OVERFLOW_CLASS}| Mana|)""")
    // " Defense" is dropped in the custom font; the glyph identifies the section.
    private val DEFENSE = Regex("""(?<defense>[0-9,.]+)${StatGlyphs.DEFENSE_CLASS}( Defense)?""")
    // Custom-font glyph only; no legacy form.
    private val VITALITY = Regex("""(?<vit>[0-9,.]+)/(?<vitMax>[0-9,.]+)${StatGlyphs.VITALITY_ALT}""")
    private val SKILL = Regex("""\+(?<gained>[0-9,.]+) (?<name>[A-Za-z]+) \((?:(?<current>[0-9,.]+)/(?<total>[0-9,.]+)|(?<percent>[0-9.]+)%)\)""")

    private val SECTION_SPLIT = Regex(" {3,}")

    // Hypixel uses US formatting ("1,390").
    private val NUMBER_FORMAT: NumberFormat = NumberFormat.getInstance(Locale.US)

    fun stats(actionBar: String): ActionBarStats {
        val sections = sections(actionBar)
        var stats = ActionBarStats()

        // Defense is omitted at 0; infer it from a 2-section health + mana bar.
        if (StatGlyphs.hasHeart(actionBar) && !StatGlyphs.hasDefense(actionBar) && sections.size == 2) {
            stats = stats.copy(defense = 0f)
        }

        for (section in sections) {
            val stripped = section.stripFormatting().trim()
            stats = when {
                StatGlyphs.hasHeart(section) -> HEALTH.find(stripped)?.let { m ->
                    stats.copy(health = m.num("health"), maxHealth = m.num("maxHealth"), wandHealing = m.num("wandHeal"))
                } ?: stats

                StatGlyphs.hasDefense(section) ->
                    DEFENSE.find(stripped)?.let { m -> stats.copy(defense = m.num("defense")) } ?: stats

                StatGlyphs.hasMana(section) -> MANA.find(stripped)?.let { m ->
                    stats.copy(mana = m.num("num"), maxMana = m.num("den"), overflowMana = m.num("overflow") ?: 0f)
                } ?: stats

                StatGlyphs.hasVitality(section) -> VITALITY.find(stripped)?.let { m ->
                    stats.copy(vitality = m.num("vit"), maxVitality = m.num("vitMax"))
                } ?: stats

                else -> stats
            }
        }
        return stats
    }

    fun skillGain(actionBar: String): SkillGain? {
        for (section in sections(actionBar)) {
            if ('(' !in section) continue
            val m = SKILL.find(section.stripFormatting().trim()) ?: continue
            val skill = skillNamed(m.named("name") ?: continue) ?: continue
            val gained = m.num("gained") ?: 0f

            m.num("percent")?.let { return SkillGain(skill, gained, it.coerceIn(0f, 100f)) }

            // total == 0 means maxed: report 100% and carry current xp.
            val cur = m.num("current") ?: continue
            val tot = m.num("total") ?: continue
            return if (tot <= 0f) {
                SkillGain(skill, gained, 100f, maxed = true, currentXp = cur)
            } else {
                SkillGain(skill, gained, (cur / tot * 100f).coerceIn(0f, 100f))
            }
        }
        return null
    }

    /** The bar's sections, with their `§` codes. */
    fun sections(actionBar: String): List<String> = actionBar.split(SECTION_SPLIT)

    /**
     * Drops the sections [drop] picks and rejoins the rest, so a feature that draws a stat itself can take it off the
     * bar. Null if nothing was dropped; empty if everything was.
     */
    fun rewriteSections(actionBar: String, drop: (section: String) -> Boolean): String? {
        val sections = sections(actionBar)
        val kept = sections.filterNot(drop)
        if (kept.size == sections.size) return null
        return kept.joinToString("   ").trim()
    }

    fun isSkillSection(section: String): Boolean {
        if ('(' !in section) return false
        val m = SKILL.find(section.stripFormatting().trim()) ?: return false
        return skillNamed(m.named("name") ?: return false) != null
    }

    private fun skillNamed(name: String): Skill? = Skill.entries.firstOrNull { it.displayName == name }

    private fun MatchResult.named(group: String): String? =
        (groups as? MatchNamedGroupCollection)?.get(group)?.value

    private fun MatchResult.num(group: String): Float? = named(group)?.let(::parseNum)

    private fun parseNum(s: String): Float? = try {
        NUMBER_FORMAT.parse(s).toFloat()
    } catch (_: ParseException) {
        null
    }
}
