package org.blackaddons.api.ui.theme

import org.blackaddons.api.ui.render.Font

/** Built-in per-HUD-element font. [INHERIT] has a null [font]. Stored by [name], so keep constant names stable. */
enum class FontChoice(val displayName: String, val font: Font?) {
    INHERIT("Inherit", null),
    MINECRAFT("Minecraft", Fonts.minecraft),
    JETBRAINS_MONO("JetBrains Mono", Fonts.regular),
    UNIFONT("Unifont", Fonts.unifont);

    companion object {
        /** Null for a key naming an installed font. */
        fun byName(name: String): FontChoice? = entries.firstOrNull { it.name == name }
    }
}
