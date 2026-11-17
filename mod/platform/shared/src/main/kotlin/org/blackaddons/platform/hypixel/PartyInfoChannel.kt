package org.blackaddons.platform.hypixel

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.blackaddons.api.hypixel.PartyRole
import org.blackaddons.core.hypixel.CoreParty
import org.blackaddons.core.hypixel.PartyTransport
import org.blackaddons.core.log.Log
import java.lang.reflect.Proxy
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
        ClientPlayNetworking.registerGlobalReceiver(Answer.TYPE) { answer, _ ->
            when {
                answer.roles != null -> CoreParty.answered(answer.roles)
                answer.error != null -> refused(answer.error)
                else -> CoreParty.refused("answer in another protocol version", rateLimited = false)
            }
        }
        // The server lists the channels it takes, so this is false off Hypixel.
        return PartyTransport("own hypixel:party_info") {
            ClientPlayNetworking.canSend(Request.TYPE).also { if (it) ClientPlayNetworking.send(Request) }
        }
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

    /** The Hypixel Mod API mod, by reflection: it's optional, and its API ships nested in its jar. */
    private object ModApiBridge {

        fun install(): PartyTransport = runCatching {
            val api = Class.forName("net.hypixel.modapi.HypixelModAPI").getMethod("getInstance").invoke(null)
            val answer = Class.forName("net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket")
            val handlerType = Class.forName("net.hypixel.modapi.handler.ClientboundPacketHandler")
            val registeredType = Class.forName("net.hypixel.modapi.handler.RegisteredHandler")
            val errorType = Class.forName("net.hypixel.modapi.handler.ErrorHandler")
            val reasonId = Class.forName("net.hypixel.modapi.error.ErrorReason").getMethod("getId")
            val packetType = Class.forName("net.hypixel.modapi.packet.HypixelPacket")
            val request = Class.forName("net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket")

            val handler = proxy(handlerType) { packet -> CoreParty.answered(roles(packet)) }
            val registered = api.javaClass.getMethod("createHandler", Class::class.java, handlerType).invoke(api, answer, handler)
            // Through the interfaces, because the classes behind them aren't public.
            val onError = proxy(errorType) { reason -> refused(reasonId.invoke(reason) as Int) }
            registeredType.getMethod("onError", errorType).invoke(registered, onError)

            val send = api.javaClass.getMethod("sendPacket", packetType)
            PartyTransport("Hypixel Mod API mod") { send.invoke(api, request.getConstructor().newInstance()) as Boolean }
        }.getOrElse { e ->
            Log.main.warn("Hypixel Mod API is installed but its party request could not be reached; Party stays empty", e)
            PartyTransport("Hypixel Mod API mod, not reachable") { false }
        }

        private fun roles(packet: Any): Map<UUID, PartyRole> {
            val members = packet.javaClass.getMethod("getMemberMap").invoke(packet) as Map<*, *>
            return members.entries.associate { (uuid, member) ->
                val role = member!!.javaClass.getMethod("getRole").invoke(member) as Enum<*>
                uuid as UUID to when (role.name) {
                    "LEADER" -> PartyRole.LEADER
                    "MOD" -> PartyRole.MODERATOR
                    else -> PartyRole.MEMBER
                }
            }
        }

        private fun proxy(type: Class<*>, body: (Any) -> Unit): Any =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
                when (method.name) {
                    "equals" -> self === args?.get(0)
                    "hashCode" -> System.identityHashCode(self)
                    "toString" -> "BlackAddons party handler"
                    else -> body(args!![0]).let { null }
                }
            }
    }
}
