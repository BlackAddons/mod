package org.blackaddons.core.soloclears

import com.google.gson.JsonObject
import org.blackaddons.api.util.Hashes
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.backend.gateway.Op
import java.util.UUID

internal enum class RunAck {
    RECORDED,

    /** A duplicate, or from an abandoned run. */
    IGNORED,

    LOST,
}

/**
 * One run on the socket. Only an ack moves the chain forward, never a sent frame, or a lost frame would fail at upload.
 * Client tick only. [RunRecorder] hands over the acks.
 */
internal class RunSession(val runId: UUID, val pulseSeconds: Int) {

    private var head: ByteArray = RunChain.EMPTY
    private var sequence = 0
    private var pending: Commitment? = null

    var finished = false
        private set

    val awaitingAck: Boolean get() = pending != null

    /** False if there was no socket. The bytes stay uncommitted and go into the next attempt. */
    fun commit(chunk: ByteArray, final: Boolean): Boolean {
        if (pending != null) return false

        val next = sequence + 1
        val digest = RunChain.link(head, chunk)
        val sent = Gateway.say(
            if (final) Op.RUN_FINISH else Op.RUN_PULSE,
            frame(next, chunk.size, Hashes.hex(digest)),
        )
        if (!sent) return false

        pending = Commitment(next, chunk, digest, final)
        return true
    }

    fun markLostConnection() {
        pending?.lost = true
    }

    /** Only resends after a loss. Resending for a slow ack would look like racing ahead. */
    fun resendLost() {
        val commitment = pending ?: return
        if (!commitment.lost) return

        val sent = Gateway.say(
            if (commitment.final) Op.RUN_FINISH else Op.RUN_PULSE,
            frame(commitment.sequence, commitment.chunk.size, Hashes.hex(commitment.digest)),
        )
        if (!sent) return

        commitment.lost = false
        commitment.resent = true
    }

    fun onAck(acknowledged: Int, error: String?): RunAck {
        val commitment = pending ?: return RunAck.IGNORED
        if (acknowledged != commitment.sequence) return RunAck.IGNORED

        // `out_of_order` on a resend means the first one landed and its ack got lost.
        val recorded = error == null || (commitment.resent && error == OUT_OF_ORDER)
        if (!recorded) return RunAck.LOST

        head = commitment.digest
        sequence = commitment.sequence
        pending = null
        if (commitment.final) finished = true
        return RunAck.RECORDED
    }

    fun abort() {
        Gateway.say(Op.RUN_ABORT, JsonObject().apply { addProperty("runId", runId.toString()) })
    }

    private fun frame(sequence: Int, bytes: Int, hash: String): JsonObject = JsonObject().apply {
        addProperty("runId", runId.toString())
        addProperty("seq", sequence)
        addProperty("bytes", bytes)
        addProperty("hash", hash)
    }

    private class Commitment(
        val sequence: Int,
        val chunk: ByteArray,
        val digest: ByteArray,
        val final: Boolean,
    ) {
        var lost = false

        /** Makes an `out_of_order` mean "already recorded". */
        var resent = false
    }

    private companion object {
        const val OUT_OF_ORDER = "out_of_order"
    }
}
