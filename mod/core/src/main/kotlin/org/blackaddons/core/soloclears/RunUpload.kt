package org.blackaddons.core.soloclears

import org.blackaddons.api.Platform
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.api.util.io.str
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.log.Log
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Uploads the recording over HTTP, because a large body would block the socket. Commitments are already made. */
internal object RunUpload {

    private const val EXCERPT = 400

    private val RECONNECT_TIMEOUT = 5.seconds

    sealed interface Outcome {

        /** [status] is `Submitted` until verified. */
        data class Accepted(val status: String, val score: Int, val elapsedSeconds: Int) : Outcome

        data class Refused(val code: String, val detail: String?) : Outcome

        /** No answer at all. Worth a retry, unlike [Unexpected]. */
        data object Unreachable : Outcome

        data class Unexpected(val status: Int) : Outcome
    }

    fun submit(runId: UUID, blob: ByteArray, onOutcome: (Outcome) -> Unit) {
        OffThread.run(
            name = "soloclear-upload",
            work = { send(runId, blob) },
            onFailure = { Outcome.Unreachable },
            onResult = onOutcome,
        )
    }

    private fun send(runId: UUID, blob: ByteArray): Outcome {
        // A refused build gets no socket back, and the upload is plain HTTP anyway.
        if (!Gateway.connected && !Gateway.unsupported) {
            BackendNotifier.ensureConnected()
            val deadline = TimeSource.Monotonic.markNow() + RECONNECT_TIMEOUT
            while (!Gateway.connected && !deadline.hasPassedNow()) {
                try {
                    Thread.sleep(100)
                } catch (ignored: InterruptedException) {
                    break
                }
            }
        }

        val response = Http.send(
            url = "${Backend.API_V1}/soloclears/runs/$runId/blob",
            bytes = blob,
            // Not `Content-Encoding: gzip`, because a proxy could unwrap it.
            contentType = "application/gzip",
            authenticated = true,
        )
        if (response == null) {
            Log.main.warn("Solo clear {} could not be handed in: no answer from the backend", runId)
            return Outcome.Unreachable
        }

        val json = parseJsonObject(response.body)

        return when {
            response.status == 201 && json != null -> Outcome.Accepted(
                status = json.str("status") ?: "Submitted",
                score = json.int("score"),
                elapsedSeconds = json.int("elapsedSeconds"),
            )

            response.status == 400 && json != null ->
                Outcome.Refused(json.str("code") ?: "rejected", json.str("details") ?: json.str("error"))

            response.status == 403 -> Outcome.Refused(NO_ACCOUNT, null)

            else -> {
                Log.main.warn(
                    "Solo clear {} was answered with HTTP {} - {}",
                    runId, response.status, response.body.take(EXCERPT).ifBlank { "(no body)" },
                )
                Outcome.Unexpected(response.status)
            }
        }
    }

    /**
     * Keeps a recording that can't be uploaded under `<configDir>/soloclears/` as evidence for manual review and bug
     * reports. The session expires, so it can't be sent again.
     */
    fun keep(runId: UUID, blob: ByteArray): Path? = runCatching {
        Platform.configDir.resolve("soloclears").resolve("$runId.jsonl.gz")
            .also { Disk.write(it, blob) }
    }.getOrElse {
        Log.main.warn("Could not keep the recording for solo clear {}", runId, it)
        null
    }

    const val NO_ACCOUNT = "no_account"

    /** On a retry, the first attempt landed. */
    const val ALREADY_SUBMITTED = "already_submitted"
}
