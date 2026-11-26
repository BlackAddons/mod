package org.blackaddons.api.skyblock.player

import org.blackaddons.api.skyblock.profile.Skill
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The only source of live health, mana and defense, and Hypixel keeps reshaping it: a regex that stops matching fails
 * here instead of showing an empty bar.
 */
class ActionBarTest {

    @Test
    fun `reads health, defense and mana off a plain bar`() {
        val stats = ActionBar.stats("§c1390/1390❤     §a720§a❈ Defense     §b183/171✎ Mana§r")
        assertEquals(1390f, stats.health)
        assertEquals(1390f, stats.maxHealth)
        assertEquals(720f, stats.defense)
        assertEquals(183f, stats.mana)
        assertEquals(171f, stats.maxMana)
        assertNull(stats.wandHealing)
    }

    @Test
    fun `reads the healing wand's tick and the overflow mana`() {
        val stats = ActionBar.stats("§61181/1161❤+§c20▆     §a593§a❈ Defense     §b501/550✎ -10ʬ")
        assertEquals(1181f, stats.health)
        assertEquals(20f, stats.wandHealing)
        assertEquals(-10f, stats.overflowMana)
        assertEquals(501f, stats.mana)
    }

    @Test
    fun `infers zero defense from the section Hypixel omits`() {
        // At 0 defense the section is gone; that isn't "unchanged".
        val stats = ActionBar.stats("§c100/100❤     §b50/50✎ Mana")
        assertEquals(0f, stats.defense)
    }

    @Test
    fun `leaves an absent stat null rather than zeroing it`() {
        // More than two sections and no defense: nothing to infer.
        val stats = ActionBar.stats("§c100/100❤     §b50/50✎ Mana     §7Some Event")
        assertNull(stats.defense)
    }

    @Test
    fun `parses US-grouped numbers`() {
        val stats = ActionBar.stats("§c1,390/2,000❤     §b183/171✎ Mana")
        assertEquals(1390f, stats.health)
        assertEquals(2000f, stats.maxHealth)
    }

    @Test
    fun `derives a skill percent from the xp pair`() {
        val sample = ActionBar.skillGain("§3+10.9 Combat (300,000/600,000)")
        assertEquals(Skill.COMBAT, sample?.skill)
        assertEquals(10.9f, sample?.gained)
        assertEquals(50f, sample?.percent)
        assertFalse(sample!!.maxed)
    }

    @Test
    fun `takes the percent verbatim when Hypixel prints one`() {
        val sample = ActionBar.skillGain("§3+5 Combat (52%)")
        assertEquals(52f, sample?.percent)
    }

    @Test
    fun `treats a zero total as a maxed skill and keeps the raw xp`() {
        // No next level; naive division would crash or give NaN.
        val sample = ActionBar.skillGain("§3+60 Runecrafting (118,084/0)")
        assertTrue(sample!!.maxed)
        assertEquals(100f, sample.percent)
        assertEquals(118084f, sample.currentXp)
    }

    @Test
    fun `ignores a pop-up for something that is not a skill`() {
        assertNull(ActionBar.skillGain("§3+5 Pigeon (52%)"))
        assertFalse(ActionBar.isSkillSection("§3+5 Pigeon (52%)"))
        assertTrue(ActionBar.isSkillSection("§3+5 Combat (52%)"))
    }

    @Test
    fun `rewriteSections drops only what the predicate names, and reports when nothing went`() {
        val bar = "§c100/100❤     §a10§a❈ Defense     §b50/50✎ Mana"
        // Rejoined with three spaces (the minimum break), not the original spacing.
        val withoutMana = ActionBar.rewriteSections(bar) { StatGlyphs.hasMana(it) }
        assertEquals("§c100/100❤   §a10§a❈ Defense", withoutMana)
        // Nothing matched: null, leave the bar alone.
        assertNull(ActionBar.rewriteSections(bar) { false })
        // Everything matched: empty, not null.
        assertEquals("", ActionBar.rewriteSections(bar) { true })
    }

    // Hypixel picks PUA glyphs by font setting; built from [Symbols] since literal PUA glyphs are invisible in editors.
    @Test
    fun `reads the custom-font PUA glyphs Hypixel sends with its resource pack`() {
        val bar = "§c1390/1390${StatGlyphs.HEART_ALT}     §a720§a${StatGlyphs.DEFENSE_ALT} Defense     " +
            "§b183/171${StatGlyphs.MANA_ALT} Mana§r"
        val stats = ActionBar.stats(bar)
        assertEquals(1390f, stats.health)
        assertEquals(720f, stats.defense)
        assertEquals(183f, stats.mana)
        assertEquals(171f, stats.maxMana)
    }

    @Test
    fun `reads overflow mana written with the PUA glyph`() {
        val stats = ActionBar.stats("§b501/550${StatGlyphs.MANA_ALT} -10${StatGlyphs.OVERFLOW_ALT}")
        assertEquals(-10f, stats.overflowMana)
        assertEquals(501f, stats.mana)
    }

    @Test
    fun `infers zero defense from a PUA-encoded two-section bar`() {
        val stats = ActionBar.stats("§c100/100${StatGlyphs.HEART_ALT}     §b50/50${StatGlyphs.MANA_ALT} Mana")
        assertEquals(0f, stats.defense)
    }

    @Test
    fun `reads the vitality section from a real full dump`() {
        // Exact 2026-07-24 dump, all custom font.
        val bar = "§64,038/3,663${StatGlyphs.HEART_ALT}     §a789${StatGlyphs.DEFENSE_ALT}     " +
            "§b2,775/2,775${StatGlyphs.MANA_ALT} §3600${StatGlyphs.OVERFLOW_ALT}     §4122/122${StatGlyphs.VITALITY_ALT}"
        val stats = ActionBar.stats(bar)
        assertEquals(4038f, stats.health)
        assertEquals(3663f, stats.maxHealth)
        assertEquals(789f, stats.defense)
        assertEquals(2775f, stats.mana)
        assertEquals(600f, stats.overflowMana)
        assertEquals(122f, stats.vitality)
        assertEquals(122f, stats.maxVitality)
    }

    @Test
    fun `leaves vitality null when the section is absent`() {
        val stats = ActionBar.stats("§c1390/1390❤     §b183/171✎ Mana")
        assertNull(stats.vitality)
        assertNull(stats.maxVitality)
    }
}
