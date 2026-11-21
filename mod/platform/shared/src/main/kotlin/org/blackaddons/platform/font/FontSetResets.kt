package org.blackaddons.platform.font

// Counts font set atlas resets, so FontManager's cached glyph lookups know their glyphs may point into freed pages.
object FontSetResets {
    @Volatile
    @JvmStatic
    var count = 0
        private set

    @JvmStatic
    fun reset() {
        count++
    }
}
