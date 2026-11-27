package org.blackaddons.core.backend.gateway

import com.google.gson.JsonObject
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.BackendStatus
import org.blackaddons.core.backend.chat.ChatService
import org.blackaddons.core.backend.chat.ChatStore
import org.blackaddons.core.backend.tickets.TicketService
import org.blackaddons.core.cosmetics.store.CosmeticStore
import org.blackaddons.core.soloclears.RunRecorder
import org.blackaddons.core.update.UpdateCheck
import java.util.UUID

/**
 * Routes the frames of a live socket. It's the only place that grows when the gateway learns something new. Runs on
 * HTTP client threads, so nothing here touches Minecraft.
 */
internal object GatewayRouter : GatewayHandler {

    @Volatile private var account: UUID? = null

    override fun onReady(payload: JsonObject) {
        val ready = Uuids.parse(payload.stringOrNull("playerUuid"))
        // Before the resend below, because the unacknowledged lines were typed by the previous player.
        if (account != null && ready != account) switchedAccount()
        account = ready

        // SERVER_STATUS only comes on a change, so a socket that missed the "ok" would stay degraded forever.
        BackendStatus.onServerStatus(degraded = false)
        cosmeticsVersion(payload)
        // Chat sends again anything unacked on the dead socket, with its original nonce.
        ChatStore.onReady(payload)
        ChatService.onReady()
        // Replies that arrived while offline are only in the list.
        TicketService.onReady()
    }

    /**
     * IAS or the Auth addon changed the player while the client was running. Nothing from the previous player carries
     * over.
     */
    private fun switchedAccount() {
        ChatService.onAccountSwitched()
        ChatStore.onAccountSwitched()
        TicketService.onAccountSwitched()
    }

    override fun onFrame(op: String, payload: JsonObject) {
        when (op) {
            Op.HEARTBEAT_ACK -> cosmeticsVersion(payload)

            Op.EVENT -> event(payload)

            Op.PRESENCE -> Presence.onFrame(payload)

            // Edits take the same path. The chat service decides what game chat gets.
            Op.MESSAGE, Op.MESSAGE_EDIT -> ChatService.onMessage(payload)

            // Matched by nonce. A refusal must not close the socket.
            Op.MESSAGE_ACK -> ChatService.onAck(payload)

            Op.TYPING -> ChatService.onTyping(payload)

            Op.TICKET -> TicketService.onFrame(payload)

            // Only sent on transitions. There's no "all is well" frame.
            Op.SERVER_STATUS -> BackendStatus.onServerStatus(payload.stringOrNull("state") == DEGRADED)

            // Handed over: only the tick that owns the recording can move the chain forward.
            Op.RUN_START_ACK, Op.RUN_ACK -> RunRecorder.onFrame(op, payload)

            else -> Unit
        }
    }

    /** Events only signal a change. The read goes through ETags and caches. */
    private fun event(payload: JsonObject) {
        when (payload.stringOrNull("type")) {
            EventType.COSMETICS ->
                CosmeticStore.onCosmeticsChanged(payload.stringOrNull("version"), playersNamed(payload))

            EventType.VERSION -> UpdateCheck.recheck()

            // `version` holds the deleted id. Sent to the whole channel.
            EventType.CHAT_DELETED -> payload.stringOrNull("version")?.toLongOrNull()?.let(ChatStore::onDeleted)

            else -> Unit
        }
    }

    /** Anything else, including new words for "fine", means fine. */
    private const val DEGRADED = "degraded"

    private fun cosmeticsVersion(payload: JsonObject) {
        payload.stringOrNull("cosmeticsVersion")?.let { CosmeticStore.onCatalogVersion(it) }
    }

    /**
     * Null means no players are named, so the catalog moved. Unreadable UUIDs get dropped one by one, so they never
     * cause a full lobby re-resolve.
     */
    internal fun playersNamed(payload: JsonObject): List<UUID>? {
        val array = payload.get("players")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        if (array.isEmpty) return null
        return array.mapNotNull { element ->
            Uuids.parse(element?.takeIf { it.isJsonPrimitive }?.asString)
        }
    }
}
