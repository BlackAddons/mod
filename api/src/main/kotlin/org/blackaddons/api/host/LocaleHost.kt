package org.blackaddons.api.host

/**
 * Player's language as a Minecraft locale code (`en_us`, `ru_ru`). Translation itself is
 * [org.blackaddons.api.util.I18n]'s job.
 */
interface LocaleHost {
    val current: String
}
