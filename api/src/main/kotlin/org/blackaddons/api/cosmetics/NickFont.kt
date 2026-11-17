package org.blackaddons.api.cosmetics

/** A Fontsource font on a cosmetic name, pinned to one version: the backend's `font` object. */
data class NickFont(
    val id: String,
    val family: String,
    val weight: Int,
    val italic: Boolean,
    /** Only the unicode subsets the name's letters need. */
    val files: List<NickFontFile>,
) {
    /** One font per family, weight and style. All players wearing it share it, whichever subsets each of them needs. */
    val familyKey: String
        get() = "$id-$weight${if (italic) "-italic" else ""}"
}

/** [range] is CSS `unicode-range` syntax, e.g. `U+0000-00FF,U+0131`. */
data class NickFontFile(val subset: String, val url: String, val range: String) {
    private val spans: List<IntRange> by lazy { parse(range) }

    fun covers(codePoint: Int): Boolean = spans.any { codePoint in it }

    private companion object {
        fun parse(range: String): List<IntRange> = range.split(',').mapNotNull { part ->
            val bounds = part.trim().removePrefix("U+").removePrefix("u+").split('-')
            val from = bounds[0].toIntOrNull(16) ?: return@mapNotNull null
            val to = bounds.getOrNull(1)?.let { it.toIntOrNull(16) ?: return@mapNotNull null } ?: from
            from..to
        }
    }
}
