package org.blackaddons.platform.event

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.BrandPayload
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.network.CustomPayloadSendEvent
import org.blackaddons.core.log.Log
import org.blackaddons.platform.event.impl.PacketEvent
import org.blackaddons.platform.util.PayloadHelper

/**
 * Posts outgoing custom payloads as [CustomPayloadSendEvent] and applies the result: cancel drops, an announce list
 * re-sends the handshake. Lives here because rebuilding needs reflection over Fabric internals ([PayloadHelper]).
 */
object CustomPayloadEmitter {

    /** Marks our own re-send so the listener ignores it. */
    private val resending = ThreadLocal.withInitial { false }

    fun install() {
        EventListener(PacketEvent.C2S.Pre::class.java, EventPriority.NORMAL) { onSend(event) }.register()
    }

    private fun onSend(event: PacketEvent.C2S.Pre) {
        if (resending.get()) return
        val packet = event.packet as? ServerboundCustomPayloadPacket ?: return
        val payload = packet.payload()
        // The brand has its own route (ClientBrandEvent). Vanilla always sends one, so a missing one stands out.
        if (payload is BrandPayload) return

        val channel = payload.type().id.toString()
        val registration = channel == "minecraft:register" || channel == "minecraft:unregister"
        val announced = if (registration) PayloadHelper.readChannels(payload).orEmpty() else emptyList()

        val decision = EventBus.post(CustomPayloadSendEvent(channel, registration, announced))
        if (decision.cancelled) {
            event.cancelled = true
            return
        }

        val announce = decision.announceChannels?.takeIf { registration } ?: return
        val rewritten = PayloadHelper.createRegisterPayload(payload, announce.toSet())
        if (rewritten == null) {
            // Sending the original beats dropping a handshake the server might be waiting on.
            Log.main.warn("No compatible payload constructor for $channel - sent unchanged")
            return
        }
        event.cancelled = true
        resend(ServerboundCustomPayloadPacket(rewritten))
    }

    private fun resend(packet: ServerboundCustomPayloadPacket) {
        val handler = Minecraft.getInstance().connection ?: return
        resending.set(true)
        try {
            handler.connection.send(packet)
        } finally {
            resending.set(false)
        }
    }
}
