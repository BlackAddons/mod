package org.blackaddons.core.backend.gateway

import com.google.gson.JsonObject
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.core.backend.gateway.Op.MESSAGE_SEND
import org.blackaddons.core.backend.gateway.Op.RUN_START

/**
 * Gateway wire format `{"op", "d"}`, same as the backend. Unknown opcodes, events and fields get ignored, never fatal,
 * so backend additions don't break released mods.
 */
internal object Op {

    const val HELLO = "HELLO"
    const val READY = "READY"
    const val EVENT = "EVENT"
    const val VERSION = "VERSION"
    const val HEARTBEAT_ACK = "HEARTBEAT_ACK"
    const val SERVER_STATUS = "SERVER_STATUS"
    const val RECONNECT = "RECONNECT"
    const val ERROR = "ERROR"

    /** S->C. Joins, leaves and subscription answers, always a list. */
    const val PRESENCE = "PRESENCE"

    const val MESSAGE = "MESSAGE"

    const val MESSAGE_EDIT = "MESSAGE_EDIT"

    /** S->C. One per [MESSAGE_SEND], matched by nonce: the id or the refusal. Never closes the socket. */
    const val MESSAGE_ACK = "MESSAGE_ACK"

    /** S->C. Comes back to its author too, so filter by uuid. */
    const val TYPING = "TYPING"

    /** S->C. The [RUN_START] session: id, server start time and commit cadence, or why there's none. */
    const val RUN_START_ACK = "RUN_START_ACK"

    /** S->C, by `seq`. Only this frame moves the chain forward. An unstored commitment doesn't match the upload. */
    const val RUN_ACK = "RUN_ACK"

    /** S->C, this player only. A ticket line, change or removal (`kind`), with the ticket. */
    const val TICKET = "TICKET"

    const val IDENTIFY = "IDENTIFY"
    const val HEARTBEAT = "HEARTBEAT"

    /** C->S. Players whose presence we want. The reply is their current state. It only narrows, never grants. */
    const val PRESENCE_SUBSCRIBE = "PRESENCE_SUBSCRIBE"

    const val PRESENCE_UNSUBSCRIBE = "PRESENCE_UNSUBSCRIBE"

    /** C->S. `nonce` is mandatory: a retry with the same nonce returns the id of the first attempt. */
    const val MESSAGE_SEND = "MESSAGE_SEND"

    /** C->S. Unanswered. Throttled to once per 3 s. */
    const val TYPING_START = "TYPING_START"

    /**
     * C->S. Asks the server to witness a solo clear. Sent at Mort's line, not on entry, so the window runs on server
     * time.
     */
    const val RUN_START = "RUN_START"

    /**
     * C->S. Chain link: `sha256(previous || bytes since last)`. The server can't verify it, but the upload has to
     * reproduce the chain, so a forgery would have to happen live.
     */
    const val RUN_PULSE = "RUN_PULSE"

    /** C->S. Last link, when the score crosses the threshold. The upload follows. */
    const val RUN_FINISH = "RUN_FINISH"

    const val RUN_ABORT = "RUN_ABORT"
}

/** Anything else comes from a newer backend. */
internal object EventType {
    const val COSMETICS = "cosmetics"
    const val VERSION = "version"
    const val CHAT_DELETED = "chat_deleted"
}

/** Sent with [Op.ERROR], always the last frame before close. */
internal object GatewayError {
    /** The only code where a reconnect can't help. */
    const val VERSION_UNSUPPORTED = "VERSION_UNSUPPORTED"

    const val INVALID_TOKEN = "INVALID_TOKEN"
}

/** [d] is never null. A missing payload reads as an empty object. */
internal data class Frame(val op: String, val d: JsonObject) {

    fun encode(): String = JsonObject().apply {
        addProperty("op", op)
        add("d", d)
    }.toString()

    companion object {
        fun decode(text: String): Frame? {
            val root = parseJsonObject(text) ?: return null
            val op = root.get("op")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: return null
            val payload = root.get("d")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            return Frame(op, payload)
        }
    }
}

/**
 * Null when absent or not a string. Stricter than profile readers on purpose, because a missing protocol field matters.
 */
internal fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }

internal fun JsonObject.intOrNull(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

internal fun JsonObject.longOrNull(key: String): Long? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

internal fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
