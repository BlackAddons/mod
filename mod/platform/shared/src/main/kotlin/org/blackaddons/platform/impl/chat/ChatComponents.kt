package org.blackaddons.platform.impl.chat
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.objects.PlayerSprite
import net.minecraft.resources.Identifier
import net.minecraft.world.item.component.ResolvableProfile
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.ChatSpan
import java.awt.Color
import java.net.URI
import java.util.Optional

// The single ChatMessage -> Component conversion, shared by the chat host and commands.
internal fun ChatMessage.toComponent(): Component {
    val root = Component.empty()
    for (span in spans) {
        var style = Style.EMPTY
        span.color?.let { style = style.withColor(TextColor.fromRgb(it.rgb and 0xFFFFFF)) }
        if (span.bold) style = style.withBold(true)
        if (span.italic) style = style.withItalic(true)
        if (span.underlined) style = style.withUnderlined(true)
        if (span.strikethrough) style = style.withStrikethrough(true)
        if (span.obfuscated) style = style.withObfuscated(true)
        span.click?.let { style = style.withClickEvent(it.toClickEvent()) }
        span.hover?.let { style = style.withHoverEvent(HoverEvent.ShowText(Component.literal(it))) }
        span.font?.let { style = style.withFont(FontDescription.Resource(Identifier.parse(it))) }
        val text = Component.literal(span.text)
        val content = span.head?.let { Component.`object`(PlayerSprite(ResolvableProfile.createUnresolved(it), true), text) } ?: text
        root.append(content.withStyle(style))
    }
    return root
}

/**
 * Component -> spans. `visit` resolves inherited style, so each run has its rendered style. Hover gets flattened to
 * plain text, every reader matches text anyway.
 */
internal fun Component.toChatMessage(): ChatMessage {
    val spans = ArrayList<ChatSpan>()
    visit({ style, text ->
        spans += ChatSpan(
            text = text,
            color = style.color?.let { Color(it.value) },
            bold = style.isBold,
            italic = style.isItalic,
            underlined = style.isUnderlined,
            strikethrough = style.isStrikethrough,
            obfuscated = style.isObfuscated,
            click = style.clickEvent?.toChatClick(),
            hover = (style.hoverEvent as? HoverEvent.ShowText)?.value()?.string,
            font = (style.font as? FontDescription.Resource)?.id()?.takeIf { style.font != FontDescription.DEFAULT }
                ?.toString(),
        )
        Optional.empty<Unit>() // empty = keep visiting
    }, Style.EMPTY)
    return ChatMessage(spans)
}

private fun ClickEvent.toChatClick(): ChatClick? = when (this) {
    // The platform adds the slash on the way out.
    is ClickEvent.RunCommand -> ChatClick.RunCommand(command().removePrefix("/"))
    is ClickEvent.SuggestCommand -> ChatClick.SuggestCommand(command().removePrefix("/"))
    is ClickEvent.OpenUrl -> ChatClick.OpenUrl(uri().toString())
    is ClickEvent.CopyToClipboard -> ChatClick.CopyToClipboard(value())
    else -> null // actions we don't model (change page, dialog)
}

private fun ChatClick.toClickEvent(): ClickEvent = when (this) {
    is ChatClick.RunCommand -> ClickEvent.RunCommand("/$command")
    is ChatClick.SuggestCommand -> ClickEvent.SuggestCommand("/$command")
    is ChatClick.OpenUrl -> ClickEvent.OpenUrl(URI.create(url))
    is ChatClick.CopyToClipboard -> ClickEvent.CopyToClipboard(text)
}
