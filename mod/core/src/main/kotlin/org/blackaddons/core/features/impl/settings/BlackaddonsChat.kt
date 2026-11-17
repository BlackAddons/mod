package org.blackaddons.core.features.impl.settings

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.chat.ChatSendEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.chat.ChatChannel
import org.blackaddons.core.backend.chat.ChatService
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.ChatAddress
import org.blackaddons.core.chat.ChatPrefix
import org.blackaddons.core.chat.brand
import org.blackaddons.core.ui.screen.chat.ChatScreen

@AlwaysActive
object BlackaddonsChat : Feature(
    description = "The BlackAddons chat: a prefix in the game chat, and a window for the rest.",
    name = "BlackAddons Chat",
) {
    private val prefixSetting by string(
        name = "Chat Prefix",
        default = "#",
        placeholder = "#",
        maxLength = 3,
        // Letters, digits or spaces would catch normal chat. Empty disables the prefix.
        accepts = { value -> value.none { it.isLetterOrDigit() || it.isWhitespace() } },
    ).withDescription(
        "Type this in front of a message in the game chat to say it in BlackAddons global instead. " +
            "Double it to send a message that really starts with it. Empty turns it off.",
    )

    // Never empty: the mirror recognizes its own lines by this clickable first span.
    private val tagSetting by string(
        name = "Game Chat Tag",
        default = DEFAULT_TAG,
        placeholder = DEFAULT_TAG,
        maxLength = 16,
        accepts = { it.isNotBlank() },
    ).withDescription("Shown in front of BlackAddons chat lines in the game chat. Click it to open the chat window.")

    private val openKey by keybind(name = "Open Chat")
        .withDescription("Opens the chat window. Also on /blackaddons chat.")

    private val mirrorSetting by boolean(
        name = "Show In Game Chat",
        default = true,
    ).withDescription(
        "Echo new lines into the game chat while the window is closed, so a conversation is visible " +
            "without opening anything.",
    )

    private val imagePreviewSetting by boolean(
        name = "Image Preview",
        default = true,
    ).withDescription(
        "Hover a BlackAddons line in the game chat to see the picture it links to. Minecraft's own " +
            "hover cannot hold an image, so this is drawn over the chat.",
    )

    private val openButton by button(
        name = "Chat Window",
        action = { Platform.scheduler.schedule(0) { ChatScreen.open() } },
        label = "Open",
    )

    /*
     * `/chat b`: plain game chat goes to BlackAddons global until another /chat channel. Not saved on purpose: after a
     * restart nobody should say into IRC what they meant for the game.
     */
    @Volatile private var ircMode = false

    val tag: String get() = tagSetting.value.trim().ifEmpty { DEFAULT_TAG }
    val mirror: Boolean get() = mirrorSetting.value
    val imagePreview: Boolean get() = imagePreviewSetting.value

    override fun init() {
        onKeybind(openKey) {
            ChatScreen.open()
        }

        register<ChatSendEvent> {
            if (event.isCommand) return@register switchChannel(event)
            when (val address = ChatPrefix.read(event.message, prefixSetting.value)) {
                is ChatAddress.Server -> Unit
                is ChatAddress.Escaped -> event.message = address.message

                // Always cancel first. Passing it with the socket down leaks private lines into public server chat.
                is ChatAddress.Ours -> {
                    event.cancelled = true
                    if (address.text.isNotEmpty()) ChatService.send(ChatChannel.GLOBAL, address.text)
                }
            }
        }

        // Last, so a Chat Tabs channel tab still sends where it says.
        register<ChatSendEvent>(EventPriority.LOWEST) {
            if (!ircMode || event.isCommand || event.cancelled) return@register
            event.cancelled = true
            val text = event.message.trim()
            if (text.isNotEmpty()) ChatService.send(ChatChannel.GLOBAL, text)
        }
    }

    // Ours gets swallowed, any other `/chat <channel>` leaves our mode and still reaches Hypixel.
    private fun switchChannel(event: ChatSendEvent) {
        val args = event.message.trim().lowercase().split(' ')
        if (args.size != 2 || args[0] != "chat") return
        val ours = args[1] in ChatPrefix.CHANNEL
        if (ours) event.cancelled = true
        if (ours == ircMode) return
        ircMode = ours
        val key = if (ours) "blackaddons.bachat.mode.on" else "blackaddons.bachat.mode.off"
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr(key), Brand.BODY)
        })
    }

    private const val DEFAULT_TAG = "[BA]"
}
