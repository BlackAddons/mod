package org.blackaddons.platform.hypixel

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.blackaddons.api.hypixel.PartyRole
import org.blackaddons.core.hypixel.CoreParty
import org.blackaddons.core.hypixel.PartyTransport
import org.blackaddons.core.log.Log
import java.util.UUID

/**
 * Hypixel's `hypixel:party_info` request and answer (Mod API protocol version 2). When the Hypixel Mod API mod is
 * installed it owns the channel, and registering it a second time would break it for every mod that uses it, so the
 * request goes through that mod instead.
 */
internal object PartyInfoChannel {

    private const val MOD_API = "hypixel-mod-api"
    private const val VERSION = 2
    private const val RATE_LIMITED = 3

    // The Mod API's error ids.
    private val ERRORS = mapOf(
        1 to "disabled", 2 to "internal server error", RATE_LIMITED to "rate limited",
        4 to "invalid packet version", 5 to "no longer supported",
    )

    private fun refused(id: Int) = CoreParty.refused(ERRORS[id] ?: "error $id", rateLimited = id == RATE_LIMITED)

    fun install() {
        CoreParty.transport = if (FabricLoader.getInstance().isModLoaded(MOD_API)) ModApiBridge.install() else ownChannel()
        CoreParty.start()
    }

    private fun ownChannel(): PartyTransport {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(Answer.TYPE, Answer.CODEC)
        PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(Hello.TYPE, Hello.CODEC)
        ClientConfigurationNetworking.registerGlobalReceiver(Hello.TYPE) { _, _ -> onHypixel = true }
        ClientPlayNetworking.registerGlobalReceiver(Hello.TYPE) { _, _ -> onHypixel = true }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> onHypixel = false }
        ClientPlayNetworking.registerGlobalReceiver(Answer.TYPE) { answer, _ ->
            when {
                answer.roles != null -> CoreParty.answered(answer.roles)
                answer.error != null -> refused(answer.error)
                else -> CoreParty.refused("answer in another protocol version", rateLimited = false)
            }
        }
        // Hypixel never lists its channels, so canSend stays false there. Its hello is the sign, like the Mod API mod.
        return PartyTransport("own hypixel:party_info") {
            (onHypixel && Minecraft.getInstance().connection != null).also { if (it) ClientPlayNetworking.send(Request) }
        }
    }

    @Volatile
    private var onHypixel = false

    private object Hello : CustomPacketPayload {
        val TYPE = CustomPacketPayload.Type<Hello>(Identifier.fromNamespaceAndPath("hypixel", "hello"))
        val CODEC: StreamCodec<FriendlyByteBuf, Hello> = CustomPacketPayload.codec({ _, _ -> }) { buf ->
            buf.skipBytes(buf.readableBytes())
            Hello
        }

        override fun type() = TYPE
    }

    private val ID = Identifier.fromNamespaceAndPath("hypixel", "party_info")

    private object Request : CustomPacketPayload {
        val TYPE = CustomPacketPayload.Type<Request>(ID)
        val CODEC: StreamCodec<FriendlyByteBuf, Request> = CustomPacketPayload.codec({ _, buf -> buf.writeVarInt(VERSION) }) { buf ->
            buf.readVarInt()
            Request
        }

        override fun type() = TYPE
    }

    /** [roles] is the party (empty outside one), or null with an [error] id when the server refused, or neither. */
    private class Answer(val roles: Map<UUID, PartyRole>?, val error: Int?) : CustomPacketPayload {
        override fun type() = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<Answer>(ID)
            val CODEC: StreamCodec<FriendlyByteBuf, Answer> = CustomPacketPayload.codec({ _, _ -> }, ::read)

            private fun read(buf: FriendlyByteBuf): Answer {
                if (!buf.readBoolean()) return Answer(null, buf.readVarInt())
                if (buf.readVarInt() != VERSION) {
                    buf.skipBytes(buf.readableBytes())
                    return Answer(null, null)
                }
                if (!buf.readBoolean()) return Answer(emptyMap(), null)
                val roles = (0 until buf.readVarInt()).associate { buf.readUUID() to ROLES[buf.readVarInt()] }
                return Answer(roles, null)
            }

            // In the order of the protocol's ids.
            private val ROLES = listOf(PartyRole.LEADER, PartyRole.MODERATOR, PartyRole.MEMBER)
        }
    }

    /** The Hypixel Mod API mod. Only touched when it's installed, so its classes never load otherwise. */
    private object ModApiBridge {

        fun install(): PartyTransport = runCatching {
            val api = HypixelModAPI.getInstance()
            api.createHandler(ClientboundPartyInfoPacket::class.java) { packet -> CoreParty.answered(roles(packet)) }
                .onError { reason -> refused(reason.id) }
            PartyTransport("Hypixel Mod API mod") { api.sendPacket(ServerboundPartyInfoPacket()) }
        }.getOrElse { e ->
            Log.main.warn("Hypixel Mod API is installed but its party request could not be reached; Party stays empty", e)
            PartyTransport("Hypixel Mod API mod, not reachable") { false }
        }

        private fun roles(packet: ClientboundPartyInfoPacket): Map<UUID, PartyRole> = packet.memberMap.mapValues { (_, member) ->
            when (member.role) {
                ClientboundPartyInfoPacket.PartyRole.LEADER -> PartyRole.LEADER
                ClientboundPartyInfoPacket.PartyRole.MOD -> PartyRole.MODERATOR
                else -> PartyRole.MEMBER
            }
        }
    }
}
