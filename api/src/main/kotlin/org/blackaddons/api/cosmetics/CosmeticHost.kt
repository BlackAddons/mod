package org.blackaddons.api.cosmetics

import java.util.UUID

/**
 * Per-aspect cosmetic lookup (name, size, cape, items), since each can come from a different source. Returns
 * null when there's nothing, or when other players' cosmetics are hidden. MC-free: a cape is a URL.
 */
interface CosmeticHost {

    fun scaleFor(uuid: UUID?): Scale?

    fun nameFor(uuid: UUID?): Cosmetic?

    fun capeUrlFor(uuid: UUID?): String?

    fun itemsFor(uuid: UUID?): List<Cosmetic3dItem> = emptyList()

    /**
     * A local path to [file]. Null while it is still downloading (asking starts the download) or when it cannot
     * be downloaded.
     */
    fun fontFile(file: NickFontFile): String? = null

    companion object None : CosmeticHost {
        override fun scaleFor(uuid: UUID?): Scale? = null
        override fun nameFor(uuid: UUID?): Cosmetic? = null
        override fun capeUrlFor(uuid: UUID?): String? = null
        override fun itemsFor(uuid: UUID?): List<Cosmetic3dItem> = emptyList()
    }
}
