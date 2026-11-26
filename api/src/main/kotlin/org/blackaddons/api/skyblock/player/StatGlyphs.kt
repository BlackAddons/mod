package org.blackaddons.api.skyblock.player


/**
 * Action-bar stat glyphs. Hypixel uses the legacy symbol or a Private Use Area one depending on the font
 * setting, so parsers must accept both ([hasHeart] etc., or the `*_CLASS` fragments). Bare constants are the
 * ones we draw. `_ALT` values are escapes because PUA glyphs are invisible in editors.
 */
object StatGlyphs {
    const val HEART = "❤"
    const val MANA = "✎"
    const val DEFENSE = "❈"
    const val OVERFLOW = "ʬ"
    const val VITALITY = "♨"

    const val HEART_ALT = "\uE010"
    const val MANA_ALT = "\uE003"
    const val DEFENSE_ALT = "\uE008"
    const val OVERFLOW_ALT = "\uE017"
    const val VITALITY_ALT = "\uE028"

    const val HEART_CLASS = "[$HEART$HEART_ALT]"
    const val MANA_CLASS = "[$MANA$MANA_ALT]"
    const val DEFENSE_CLASS = "[$DEFENSE$DEFENSE_ALT]"
    const val OVERFLOW_CLASS = "[$OVERFLOW$OVERFLOW_ALT]"

    fun hasHeart(text: String): Boolean = HEART in text || HEART_ALT in text
    fun hasMana(text: String): Boolean = MANA in text || MANA_ALT in text
    fun hasDefense(text: String): Boolean = DEFENSE in text || DEFENSE_ALT in text
    fun hasVitality(text: String): Boolean = VITALITY_ALT in text
}
