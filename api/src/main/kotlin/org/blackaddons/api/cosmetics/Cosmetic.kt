package org.blackaddons.api.cosmetics

/** `1` is normal, `0` flattens, negatives mirror. */
data class Scale(val x: Float, val y: Float, val z: Float) {
    companion object {
        val NONE = Scale(1f, 1f, 1f)
    }
}

/** One player's resolved cosmetic, from the server or a local feature. Null/empty/vanilla fields change nothing. */
data class Cosmetic(
    val display: String? = null,
    val name: ColorSpec = ColorSpec.VANILLA,
    /** Admin-only tag before the name (e.g. `[VIP]`). */
    val prefix: String? = null,
    /** Ignored when [prefix] is null. */
    val prefixColor: ColorSpec = ColorSpec.VANILLA,
    val size: Scale? = null,
    /** Cape URL with a `?v=` content hash, so the texture cache re-downloads only on change. */
    val capeUrl: String? = null,
    /** Worn 3D cosmetics. Not disk-cached. */
    val items: List<Cosmetic3dItem> = emptyList(),
    val style: NickStyle = NickStyle.NONE,
    /** Ignored when [prefix] is null. */
    val prefixStyle: NickStyle = NickStyle.NONE,
    /** Applies to the name only. The prefix keeps the game's font. */
    val font: NickFont? = null,
) {
    val affectsName: Boolean
        get() = !display.isNullOrEmpty() || name.overrides || !style.isPlain

    val hasPrefix: Boolean
        get() = !prefix.isNullOrEmpty()
}
