package org.blackaddons.platform.impl.chat
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.TranslatableContents
import org.blackaddons.core.features.impl.client.ModlistHider
import org.blackaddons.platform.mixin.sanitizer.LanguageAccessor
import java.util.Optional

/** Neutralizes server translation and keybind components that would reveal installed mods. */
object ComponentSanitizer {

    private val defaultLanguage: Language by lazy {
        runCatching { LanguageAccessor.invokeLoadDefault() }.getOrDefault(Language.getInstance())
    }

    /** [component] as a string, with keys from non-allowed mods neutralized. */
    @JvmStatic
    fun sanitize(component: Component, source: String): String {
        if (!ModlistHider.shouldSanitizeComponents) return component.string

        return extractSanitizedString(component, source)
    }

    private fun extractSanitizedString(component: Component, source: String): String {
        val builder = StringBuilder()
        visitContents(component.contents, source).ifPresent(builder::append)
        for (sibling in component.siblings) {
            builder.append(extractSanitizedString(sibling, source))
        }
        return builder.toString()
    }

    private fun visitContents(contents: ComponentContents, source: String): Optional<String> {
        when (contents) {
            is KeybindContents -> {
                if (!canTranslate(contents.name)) {
                    ModlistHider.onBlockedProbe(contents.name, source)
                    return Optional.of(contents.name)
                }
            }
            is TranslatableContents -> {
                if (!canTranslate(contents.key)) {
                    ModlistHider.onBlockedProbe(contents.key, source)
                    val fallback = contents.fallback ?: contents.key
                    return Optional.of(fallback)
                }
                val sanitizedArgs = Array(contents.args.size) { index ->
                    (contents.args[index] as? Component)?.let { extractSanitizedString(it, source) }
                        ?: contents.args[index]
                }
                return TranslatableContents(contents.key, contents.fallback, sanitizedArgs).visit { Optional.of(it) }
            }
        }
        return contents.visit { Optional.of(it) }
    }

    private fun canTranslate(key: String): Boolean {
        if (defaultLanguage.has(key)) return true
        if (key.startsWith("key.keyboard.") || key.startsWith("key.mouse.") || key.startsWith("options.")) return true

        val allowedMods = ModlistHider.allowedMods
        return allowedMods.any { key.startsWith("$it.") || key.contains(".$it.") }
    }
}
