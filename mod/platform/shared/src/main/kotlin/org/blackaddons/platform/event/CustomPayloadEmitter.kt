package org.blackaddons.platform.event

import net.fabricmc.fabric.impl.networking.RegistrationPayload
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.BrandPayload
import net.minecraft.resources.Identifier
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.network.CustomPayloadSendEvent
import org.blackaddons.core.log.Log
import org.blackaddons.platform.event.impl.PacketEvent

/**
 * Posts outgoing custom payloads as [CustomPayloadSendEvent] and applies the result: cancel drops, an announce list
 * re-sends the handshake. Lives here because the handshake is Fabric's internal [RegistrationPayload].
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
        val registered = payload as? RegistrationPayload
        val announced = registered?.channels()?.map(Identifier::toString).orEmpty()

        val decision = EventBus.post(CustomPayloadSendEvent(channel, registration, announced))
        if (decision.cancelled) {
            event.cancelled = true
            return
        }

        val announce = decision.announceChannels?.takeIf { registration } ?: return
        if (registered == null) {
            // Sending the original beats dropping a handshake the server might be waiting on.
            Log.main.warn("$channel is not Fabric's RegistrationPayload - sent unchanged")
            return
        }
        event.cancelled = true
        val ids = announce.toSet().mapNotNull(Identifier::tryParse)
        resend(ServerboundCustomPayloadPacket(RegistrationPayload(registered.type(), ids)))
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
