package org.blackaddons.platform.impl.chat
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketUtils
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.ActionBarReceiveEvent
import org.blackaddons.api.event.chat.ChatMessageType
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.platform.compat.unsignedContentOrNull

/**
 * Posts [ChatReceiveEvent] and [ActionBarReceiveEvent], and keeps packet dispatch, flattening and lazy spans out of the
 * mixin.
 */
object ChatReceive {

    @JvmStatic
    fun received(packet: Packet<ClientGamePacketListener>, listener: ClientGamePacketListener) {
        /*
         * Vanilla's handlers start with this same hop. Taking it first keeps the original packet away from a mod that
         * cancels the handler on the network thread and resends an edited copy (dtMap 1.0.1 cut "N/M Secrets" out of
         * the action bar that way, so recordings had no secrets). Connection catches the throw, same as for vanilla.
         */
        PacketUtils.ensureRunningOnSameThread(packet, listener, Minecraft.getInstance().packetProcessor())
        when (packet) {
            // overlay() is the action bar, not chat.
            is ClientboundSystemChatPacket ->
                if (packet.overlay()) postActionBar(packet.content()) else post(packet.content(), ChatMessageType.SYSTEM)
            is ClientboundSetActionBarTextPacket -> postActionBar(packet.text())
            // Unsigned server text sent through a chat type is a system message, decorated the way the player sees it.
            is ClientboundDisguisedChatPacket ->
                post(packet.chatType().decorate(packet.message()), ChatMessageType.SYSTEM)
            is ClientboundPlayerChatPacket -> {
                val content = packet.unsignedContentOrNull() ?: Component.literal(packet.body().content())
                post(packet.chatType().decorate(content), ChatMessageType.PLAYER)
            }
        }
    }

    private fun post(content: Component, type: ChatMessageType) {
        val raw = content.string
        EventBus.post(
            ChatReceiveEvent(raw, ChatFormatting.stripFormatting(raw) ?: raw, type) { content.toChatMessage() },
        )
    }

    private fun postActionBar(content: Component) {
        val raw = content.string
        EventBus.post(ActionBarReceiveEvent(raw, ChatFormatting.stripFormatting(raw) ?: raw))
    }
}
