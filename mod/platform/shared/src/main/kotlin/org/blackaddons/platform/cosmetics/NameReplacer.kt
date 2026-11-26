package org.blackaddons.platform.cosmetics

import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.*
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.util.FormattedCharSequence
import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.core.backend.gateway.Presence
import org.blackaddons.core.features.impl.settings.Connection
import org.blackaddons.core.profile.PlayerNames
import org.blackaddons.platform.cosmetics.NameReplacer.decorate
import org.blackaddons.platform.cosmetics.NameReplacer.decorateSequence
import org.blackaddons.platform.util.TickCache

/**
 * Replaces a player's real-name token in name tags, tab list and chat with their cosmetic name, leaving rank, level and
 * symbols intact, and marks players connected to BlackAddons.
 *
 * Matches against [PlayerNames] (every account seen), so no web lookup. Both jobs share one scan: finding the name is
 * the expensive part and runs per tag and row every frame.
 */
object NameReplacer {

    /**
     * [decorate] runs per name tag and tab row every frame. The index only changes on join, leave or resolve, so a tick
     * of staleness is invisible.
     */
    private const val ONLINE_RGB = 0x7CE0A2

    private val nameIndex = TickCache { buildIndex() }

    /** A cosmetic, an online mark, or both. Players with neither stay out, which keeps the scan short. */
    private class Mark(val cosmetic: Cosmetic?, val online: Boolean)

    /** Same answer for the rest of the tick. Handed out as a shallow copy so callers can't alter the next frame's. */
    private val decorated = TickCache { HashMap<Component, Component>() }

    fun decorate(component: Component): Component {
        val result = decorated.get().getOrPut(component) { decorate(component, hover = false) }
        return if (result === component) component else result.copy()
    }

    /** Chat gets a hover naming the real player. Only chat: nametags aren't hoverable, tab rows rebuild per frame. */
    fun decorateChat(component: Component): Component = decorate(component, hover = true)

    private fun decorate(component: Component, hover: Boolean): Component {
        val index = nameIndex.get()
        if (index.isEmpty()) return component
        val lower = component.string.lowercase()
        if (index.keys.none { lower.contains(it) }) return component
        // Hypixel's party list already has a dot... LOL.
        val shown = lower.stripFormatting()
        val dotted = index.keys.filterTo(HashSet()) { shown.contains("$it $DOT") }
        return process(component, index, dotted, hover)
    }

    /**
     * Says the name was replaced and gives the real one (needed for `/msg`). Set on the root, because gradients only
     * set colors on siblings, which inherit the rest.
     */
    private fun explained(replacement: Component, realName: String): Component =
        replacement.copy().withStyle { style ->
            style.withHoverEvent(
                HoverEvent.ShowText(
                    Component.literal(I18n.tr("blackaddons.cosmetics.name_hover_title"))
                        .withStyle { it.withColor(TextColor.fromRgb(Theme.BRAND_FROM.rgb and RGB_MASK)) }
                        .append(
                            Component.literal("\n" + I18n.tr("blackaddons.cosmetics.name_hover_real", realName))
                                .withStyle { it.withColor(TextColor.fromRgb(Theme.TEXT_MUTED.rgb and RGB_MASK)) },
                        ),
                )
            )
        }

    /**
     * The dot's hover. The mark is only drawn for live sockets, so a missing mark proves nothing, and the second line
     * says so. Built per use, because translations aren't loaded with this object.
     */
    private fun explainedMark(): Component = ONLINE_MARK.copy().withStyle { style ->
        style.withHoverEvent(
            HoverEvent.ShowText(
                Component.literal(I18n.tr("blackaddons.cosmetics.online_hover_title"))
                    .withStyle { it.withColor(TextColor.fromRgb(ONLINE_RGB)) }
                    .append(
                        Component.literal("\n" + I18n.tr("blackaddons.cosmetics.online_hover_detail"))
                            .withStyle { it.withColor(TextColor.fromRgb(Theme.TEXT_MUTED.rgb and RGB_MASK)) },
                    ),
            )
        )
    }

    /** Minecraft colors are 24-bit, drop AWT's alpha. */
    private const val RGB_MASK = 0xFFFFFF

    /** Lowercased real name -> mark. */
    private fun buildIndex(): Map<String, Mark> {
        val connection = Minecraft.getInstance().connection ?: return emptyMap()

        // Harvest, then match: Skyblock tab rows are placeholder profiles that just name people (see [PlayerNames]).
        for (info in connection.onlinePlayers) {
            PlayerNames.remember(info.profile.name, info.profile.id)
        }

        val marking = Connection.onlineMarkers && Presence.anyOnline
        val out = HashMap<String, Mark>()
        for ((name, uuid) in PlayerNames.candidates()) {
            val cosmetic = Platform.cosmetics.nameFor(uuid)?.takeIf { it.affectsName || it.hasPrefix }
            val online = marking && Presence.isOnline(uuid)
            if (cosmetic == null && !online) continue
            out[name] = Mark(cosmetic, online)
        }
        return out
    }

    private fun process(component: Component, index: Map<String, Mark>, dotted: Set<String>, hover: Boolean): MutableComponent {
        val rebuilt: MutableComponent = when (val contents = component.contents) {
            is PlainTextContents -> replaceInText(contents.text(), index, dotted, hover)
            is TranslatableContents -> {
                val newArgs = Array(contents.args.size) { i ->
                    (contents.args[i] as? Component)?.let { process(it, index, dotted, hover) } ?: contents.args[i]
                }
                MutableComponent.create(TranslatableContents(contents.key, contents.fallback, newArgs))
            }
            else -> component.plainCopy()
        }
        rebuilt.style = component.style
        for (sibling in component.siblings) {
            rebuilt.append(process(sibling, index, dotted, hover))
        }
        return rebuilt
    }

    private const val DOT = '●'

    /**
     * Only drawn for connected players, we can't claim someone doesn't have the mod. A legacy color, because tab mods
     * that flatten to `§` strings leave hex as literal `<#7CE0A2>`.
     */
    private val ONLINE_MARK: Component = Component.literal(" $DOT").withStyle(ChatFormatting.GREEN)

    private fun replaceInText(text: String, index: Map<String, Mark>, dotted: Set<String>, hover: Boolean): MutableComponent {
        val lower = text.lowercase()
        val out: MutableComponent = Component.empty()
        var pos = 0
        while (pos < text.length) {
            val (hitIdx, hitName) = nextHit(lower, pos, index.keys) ?: run {
                out.append(Component.literal(text.substring(pos)))
                return out
            }
            if (hitIdx > pos) out.append(Component.literal(text.substring(pos, hitIdx)))
            val end = hitIdx + hitName.length
            val matched = text.substring(hitIdx, end)
            val mark = index.getValue(hitName)
            val replacement = mark.cosmetic?.let { PlayerCustomization.componentFor(it, matched) }?.let { marked(it, matched) }
            out.append(replacement?.let { if (hover) explained(it, matched) else it } ?: Component.literal(matched))
            // After the name, the left side already holds the rank and our prefix.
            if (mark.online && hitName !in dotted) out.append(if (hover) explainedMark() else ONLINE_MARK)
            pos = end
        }
        return out
    }

    private fun nextHit(lower: String, from: Int, names: Set<String>): Pair<Int, String>? {
        var hitName: String? = null
        var hitIdx = -1
        for (name in names) {
            val idx = lower.indexOf(name, from)
            if (idx == -1) continue
            // Earliest wins, longest on a tie: names prefix each other, and iteration order isn't stable.
            val better = hitIdx == -1 || idx < hitIdx || (idx == hitIdx && name.length > hitName!!.length)
            if (better) {
                hitIdx = idx
                hitName = name
            }
        }
        return hitName?.let { hitIdx to it }
    }

    /** The real name as insertion: shift-click types it, and [decorateSequence] sees the name is already ours. */
    private fun marked(replacement: Component, realName: String): Component =
        replacement.copy().withStyle { it.withInsertion(realName) }

    /** Set by the HUD render hook while vanilla and other mods extract the HUD. */
    @JvmField
    var hudPass = false

    /** Render thread only: other threads would block in [TickCache] on a flag they might see stale. */
    fun inHudPass(): Boolean = hudPass && RenderSystem.isOnRenderThread()

    private val decoratedSequences = TickCache { HashMap<FormattedCharSequence, FormattedCharSequence>() }

    /**
     * HUD fallback for text other mods rebuild from flattened strings (SkyHanni's compact tab), which our components
     * never reach. Names that are already ours carry their real name as insertion and get left alone. Lowercased one
     * char per code point so indices line up, names are ASCII.
     */
    fun decorateSequence(text: FormattedCharSequence): FormattedCharSequence {
        val index = nameIndex.get()
        if (index.isEmpty()) return text
        return decoratedSequences.get().getOrPut(text) { decorateSequence(text, index) }
    }

    private fun decorateSequence(text: FormattedCharSequence, index: Map<String, Mark>): FormattedCharSequence {
        val codePoints = IntArrayList()
        val styles = ArrayList<Style>()
        val shown = StringBuilder()
        text.accept { _, style, codePoint ->
            codePoints.add(codePoint)
            styles.add(style)
            shown.append(if (Character.isBmpCodePoint(codePoint)) codePoint.toChar().lowercaseChar() else ' ')
            true
        }
        val lower = shown.toString()
        if (index.keys.none { lower.contains(it) }) return text
        val dotted = index.keys.filterTo(HashSet()) { lower.contains("$it $DOT") }

        val parts = ArrayList<FormattedCharSequence>()
        var kept = 0
        var pos = 0
        while (true) {
            val (start, name) = nextHit(lower, pos, index.keys) ?: break
            val end = start + name.length
            pos = end
            val outer = styles[start]
            val matched = String(codePoints.elements(), start, name.length)
            if (outer.insertion.equals(matched, ignoreCase = true)) continue
            val mark = index.getValue(name)
            val replacement = mark.cosmetic?.let { PlayerCustomization.componentFor(it, matched) }
            parts.add(slice(codePoints, styles, kept, start))
            parts.add(replacement?.let { restyled(marked(it, matched), outer) } ?: slice(codePoints, styles, start, end))
            if (mark.online && name !in dotted) parts.add(restyled(ONLINE_MARK, outer))
            kept = end
        }
        if (parts.isEmpty()) return text
        parts.add(slice(codePoints, styles, kept, codePoints.size))
        return FormattedCharSequence.composite(parts)
    }

    private fun slice(codePoints: IntArrayList, styles: List<Style>, from: Int, to: Int) = FormattedCharSequence { sink ->
        (from until to).all { sink.accept(it, styles[it], codePoints.getInt(it)) }
    }

    /** Our styles over the surrounding run's, as if the replacement had been a sibling there. */
    private fun restyled(component: Component, outer: Style): FormattedCharSequence {
        val sequence = component.visualOrderText
        return FormattedCharSequence { sink -> sequence.accept { i, style, codePoint -> sink.accept(i, style.applyTo(outer), codePoint) } }
    }
}
