package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatChannel
import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.chat.ChatLineAddedEvent
import org.blackaddons.api.event.chat.ChatScreenRenderEvent
import org.blackaddons.api.event.chat.ChatSendEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.TabStrip
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.chat.ChatService
import org.blackaddons.core.chat.ChatMirror
import org.blackaddons.core.chat.ChatPrefix
import java.awt.Color
import org.blackaddons.core.backend.chat.ChatChannel as BackendChannel

object ChatTabs : Feature(
    description = "Tabs above the chat input that show one channel at a time and send to it.",
    name = "Chat Tabs",
) {

    private val partyTab by boolean("Party Tab", true)
        .withDescription("Party chat and party notices")
    private val guildTab by boolean("Guild Tab", true)
        .withDescription("Guild chat")
    private val privateTab by boolean("Private Tab", true)
        .withDescription("Private messages, both ways")
    private val coopTab by boolean("Co-op Tab", true)
        .withDescription("Co-op chat")
    private val ircTab by boolean("IRC Tab", true)
        .withDescription("BlackAddons chat shown in game chat")
    private val routeMessages by boolean("Route Messages", true)
        .withDescription("Typing in a channel tab sends there: party, guild, co-op, or whoever you last messaged privately")
    private val allIgnoresChannels by boolean("All Tab Ignores Channels", true)
        .withDescription("Messages that have their own tab don't add to the All tab's unread count")
    private val followChatChannel by boolean("Follow Chat Channel", false)
        .withDescription("/chat p, /chat g, /chat coop, /chat b and /chat a also switch the tab")

    private val tr = I18n.scope("blackaddons.chat_tabs")

    private var active: Tab? = null
    private val unread = HashMap<String, Int>()
    private var partner: String? = null
    private var strip: TabStrip? = null
    private var stripTabs: List<Tab> = emptyList()
    private var stripPartner: String? = null
    private var visible = false

    override fun init() {
        register<ChatLineAddedEvent> { arrived(event.line) }
        register<ChatScreenRenderEvent> { draw(event) }
        register<MouseClickEvent> {
            if (event.isPress && Platform.screenHost.chatOpen && clicked(event.button)) event.cancelled = true
        }
        register<ChatSendEvent>(EventPriority.LOW) { if (event.isCommand) switched(event) else route(event) }
    }

    override fun onDisable() {
        select(null)
        super.onDisable()
    }

    private fun arrived(line: ChatLine) {
        val tab = tabOf(line)
        if (tab == Tab.PRIVATE) partner = line.playerMessage?.player ?: ChatChannel.conversation(line.unformatted) ?: partner
        val shownTab = tab?.takeIf(::shown)
        if (active != null && (shownTab == null || !allIgnoresChannels.value)) unread.merge(ALL, 1, Int::plus)
        if (shownTab != null && shownTab != active) unread.merge(id(shownTab), 1, Int::plus)
    }

    private fun draw(event: ChatScreenRenderEvent) {
        visible = !event.covered
        if (!visible) return
        Render.push()
        Render.scale(SCALE, SCALE)
        try {
            strip().render(event.inputX / SCALE, event.inputY / SCALE, event.mouseX / SCALE, event.mouseY / SCALE)
        } finally {
            Render.pop()
        }
    }

    private fun clicked(button: Int): Boolean {
        val strip = strip?.takeIf { visible } ?: return false
        return strip.mouseClicked(MouseButtonInput(Platform.window.mouseX / SCALE, Platform.window.mouseY / SCALE, button))
    }

    private fun switched(event: ChatSendEvent) {
        val args = event.message.trim().lowercase().split(' ')
        if (!followChatChannel.value || args.size != 2 || args[0] != "chat") return
        val tab = when (args[1]) {
            "a", "all" -> null
            "p", "party" -> Tab.PARTY
            "g", "guild" -> Tab.GUILD
            "coop", "co-op" -> Tab.COOP
            in ChatPrefix.CHANNEL -> Tab.IRC
            else -> return
        }
        // `/chat b` is ours and never reaches the server, so it arrives cancelled.
        if (event.cancelled && tab != Tab.IRC) return
        if (tab == null || shown(tab)) select(tab)
    }

    private fun route(event: ChatSendEvent) {
        if (event.cancelled || !routeMessages.value || !Platform.screenHost.chatOpen) return
        val command = when (val tab = active) {
            null -> return
            Tab.IRC -> {
                event.cancelled = true
                ChatService.send(BackendChannel.GLOBAL, event.message)
                return
            }
            Tab.PRIVATE -> partner?.let { "msg $it" }
            else -> tab.channels.first().command
        }
        event.cancelled = true
        if (command == null) {
            Toasts.show(tr("no_partner", "Nobody to message yet"), tr("no_partner_detail", "/msg someone once, then talk in Private tab."), ToastKind.ERROR)
            return
        }
        Platform.chat.sendCommand("$command ${event.message}")
    }

    private fun strip(): TabStrip {
        val shownTabs = Tab.entries.filter(::shown)
        if (active?.let { it !in shownTabs } == true) select(null)
        strip?.takeIf { shownTabs == stripTabs && partner == stripPartner }?.let { return it }
        stripTabs = shownTabs
        stripPartner = partner
        val tabs = listOf(tab(ALL, tr("all", "All"), LucideIcons.chat)) +
            shownTabs.map { tab(id(it), label(it), icon(it)) }
        return TabStrip(tabs, active?.let(::id) ?: ALL, { select(tabFor(it), fromStrip = true) }, panelColor = INPUT_BACKGROUND)
            .also { strip = it }
    }

    private fun tab(id: String, label: String, icon: VectorIcon) = TabStrip.Tab(id, label, icon = icon, badge = { unread[id] ?: 0 })

    private fun select(tab: Tab?, fromStrip: Boolean = false) {
        active = tab
        unread.remove(tab?.let(::id) ?: ALL)
        if (!fromStrip) strip = null
        Platform.chat.showOnly(tab?.let { only -> { line: ChatLine -> tabOf(line) == only } })
    }

    private fun shown(tab: Tab) = when (tab) {
        Tab.PARTY -> partyTab.value
        Tab.GUILD -> guildTab.value
        Tab.PRIVATE -> privateTab.value
        Tab.COOP -> coopTab.value
        Tab.IRC -> ircTab.value
    }

    private fun tabOf(line: ChatLine): Tab? {
        if (ChatMirror.owns(line.message)) return Tab.IRC
        val channel = line.channel ?: return null
        return Tab.entries.firstOrNull { channel in it.channels }
    }

    private fun id(tab: Tab) = tab.name.lowercase()

    private fun tabFor(id: String) = Tab.entries.firstOrNull { id(it) == id }

    private fun label(tab: Tab) = when (tab) {
        Tab.PARTY -> tr("party", "Party")
        Tab.GUILD -> tr("guild", "Guild")
        Tab.PRIVATE -> partner?.let { tr("private_to", "To %s", it) } ?: tr("private", "Private")
        Tab.COOP -> tr("coop", "Co-op")
        Tab.IRC -> tr("irc", "IRC")
    }

    private fun icon(tab: Tab) = when (tab) {
        Tab.PARTY -> LucideIcons.usersRound
        Tab.GUILD -> LucideIcons.shield
        Tab.PRIVATE -> LucideIcons.mail
        Tab.COOP -> LucideIcons.house
        Tab.IRC -> LucideIcons.hash
    }

    // Officer chat reads in the guild tab. IRC is our own chat, not a Hypixel channel.
    private enum class Tab(vararg val channels: ChatChannel) {
        PARTY(ChatChannel.PARTY),
        GUILD(ChatChannel.GUILD, ChatChannel.OFFICER),
        PRIVATE(ChatChannel.PRIVATE),
        COOP(ChatChannel.COOP),
        IRC,
    }

    private const val ALL = "all"

    private const val HEIGHT_GUI = 20f
    private const val SCALE = HEIGHT_GUI / TabStrip.HEIGHT
    private val INPUT_BACKGROUND = Color(0, 0, 0, 128)
}
