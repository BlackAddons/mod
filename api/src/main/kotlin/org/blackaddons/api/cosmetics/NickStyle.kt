package org.blackaddons.api.cosmetics

/** Vanilla text styles of a cosmetic name or prefix, mirroring the backend's `style` object. */
data class NickStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    /** Admin only. A player cannot set it from their own editor. */
    val obfuscated: Boolean = false,
) {
    val isPlain: Boolean
        get() = this == NONE

    companion object {
        val NONE = NickStyle()
    }
}
