package org.blackaddons.platform.cosmetics

import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin
import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.ColorSpec
import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.cosmetics.NickStyle
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.GradientColors
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.platform.font.NickFontSets
import org.blackaddons.platform.mixin.font.TextColorInvoker
import java.util.UUID

/**
 * Turns cosmetic facade answers into Minecraft types (cape textures, `Component`s) for the render mixins and chat
 * decorator. Animated `TextColor`s keep chroma/gradient names moving everywhere.
 */
object PlayerCustomization {

    /** Float array for the scale mixin. */
    @JvmStatic
    fun scaleFor(uuid: UUID?): FloatArray? =
        Platform.cosmetics.scaleFor(uuid)?.let { floatArrayOf(it.x, it.y, it.z) }

    /**
     * Custom cape for [uuid], or null when there's none, it's hidden, or it's still downloading (the vanilla cape stays
     * meanwhile). Called every frame, animated capes resolve to the current frame.
     */
    @JvmStatic
    fun capeFor(uuid: UUID?): Identifier? {
        val capeUrl = Platform.cosmetics.capeUrlFor(uuid) ?: return null
        // Mirrored players wear the cape rotated 180 degrees, so return a pre-rotated texture to keep the art upright.
        val mirrored = (Platform.cosmetics.scaleFor(uuid)?.y ?: 1f) < 0f
        return CapeTextures.identifierFor(capeUrl, rotated = mirrored)
    }

    /**
     * Points [state]'s cape at the custom one and forces `showCape`. The elytra stays vanilla, custom capes have no
     * elytra UVs. No-op without a ready cape. Anything that replaces `state.skin` later (the PV preview) has to call
     * this again.
     */
    @JvmStatic
    fun applyCape(state: AvatarRenderState, uuid: UUID?) {
        val cape = capeFor(uuid) ?: return
        val skin = state.skin
        /*
         * DownloadedTexture is the concrete type the pipeline binds. Its source URL is never read on the render path
         * but it's declared non-null.
         */
        val texture = ClientAsset.DownloadedTexture(cape, "")
        state.skin = PlayerSkin(skin.body(), texture, skin.elytra(), skin.model(), skin.secure())
        state.showCape = true
    }

    /**
     * `[prefix] name`, or null if the cosmetic changes neither. Falls back to [fallback] (the real name) without a
     * `display`, so colors just recolor.
     */
    fun componentFor(cosmetic: Cosmetic, fallback: String): Component? {
        if (!cosmetic.affectsName && !cosmetic.hasPrefix) return null
        // Strip § first, or styled() passes it through untouched.
        val nameText = cosmetic.display?.takeIf { it.isNotEmpty() } ?: fallback.stripFormatting()
        val nameComp = when {
            nameText.isEmpty() -> null
            cosmetic.affectsName -> styled(nameText, cosmetic.name, cosmetic.style, cosmetic.font?.let(NickFontSets::idFor))
            else -> Component.literal(nameText) // prefix only: keep the vanilla name
        }
        if (!cosmetic.hasPrefix) return nameComp
        val out: MutableComponent = Component.empty()
            .append(styled(cosmetic.prefix!!, cosmetic.prefixColor, cosmetic.prefixStyle, font = null))
            .append(Component.literal(" "))
        nameComp?.let { out.append(it) }
        return out
    }

    /** [font] is null only without a font. A not-yet-built one draws in the game's font until it's ready. */
    private fun styled(text: String, spec: ColorSpec, style: NickStyle, font: Identifier?): Component {
        // Legacy section codes pass through verbatim.
        if (text.contains('§')) return Component.literal(text)
        if (text.isEmpty()) return Component.empty()
        val colored = colored(text, spec)
        // Set on the root, because the per-letter children only set a color and inherit the rest.
        if (style.isPlain && font == null) return colored
        return colored.copy().withStyle { root -> root.with(style).let { if (font == null) it else it.withFont(FontDescription.Resource(font)) } }
    }

    // Only the styles that are on, because an explicit false would undo bold or italic from the surrounding text.
    private fun Style.with(style: NickStyle): Style {
        var out = this
        if (style.bold) out = out.withBold(true)
        if (style.italic) out = out.withItalic(true)
        if (style.underlined) out = out.withUnderlined(true)
        if (style.strikethrough) out = out.withStrikethrough(true)
        if (style.obfuscated) out = out.withObfuscated(true)
        return out
    }

    private fun colored(text: String, spec: ColorSpec): Component {
        val n = text.length
        return when {
            spec.chroma -> perChar(text) { i ->
                // Chroma spreads half a hue turn across the letters so it reads as a rainbow.
                val offset = if (n > 1)
                    ((n - 1 - i).toFloat() / (n - 1)) * GradientColors.CHROMA_HUE_SPREAD
                else 0f
                animatedColor(distinctSeed(i, 0xFFFFFF), chroma = true, stops = null, speed = spec.speed, offset = offset)
            }
            spec.gradient.isNotEmpty() && spec.animated -> {
                val colors = GradientColors.perChar(n, spec.gradient)
                perChar(text) { i ->
                    // Whole-text: phase-shift per letter so the wave flows left to right.
                    val offset = if (spec.wholeTextGradient && n > 1) (n - 1 - i).toFloat() / (n - 1) else 0f
                    animatedColor(distinctSeed(i, colors[i]), chroma = false, spec.gradient, spec.speed, offset)
                }
            }
            spec.gradient.isNotEmpty() -> {
                val colors = GradientColors.perChar(n, spec.gradient)
                perChar(text) { i -> TextColor.fromRgb(colors[i]) }
            }
            // No color of its own: inherit the surrounding one, so Hypixel's rank color stays.
            else -> spec.solidColor?.let { Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(it))) }
                ?: Component.literal(text)
        }
    }

    private inline fun perChar(text: String, color: (Int) -> TextColor): Component {
        val out: MutableComponent = Component.empty()
        for (i in text.indices) {
            out.append(Component.literal(text[i].toString()).withStyle(Style.EMPTY.withColor(color(i))))
        }
        return out
    }

    /**
     * Folds the glyph index in so each glyph's color is distinct. MC merges equal adjacent colors, which collapses the
     * animation. getValue() is overridden, so it never shows.
     */
    private fun distinctSeed(index: Int, color: Int): Int = (index shl 24) or (color and 0xFFFFFF)

    // TextColor only implements AnimatedTextColor at runtime (TextColorMixin), so kotlinc thinks the cast can't work.
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun animatedColor(
        seed: Int,
        chroma: Boolean,
        stops: List<ColorStop>?,
        speed: Float,
        offset: Float,
    ): TextColor {
        val tc = TextColorInvoker.baCreate(seed)
        (tc as AnimatedTextColor).apply {
            if (chroma) baSetChroma(true)
            if (!chroma && stops != null) {
                baSetAnimated(true)
                baSetStops(stops)
            }
            baSetSpeed(speed)
            baSetOffset(offset)
        }
        return tc
    }
}
