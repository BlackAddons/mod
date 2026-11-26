package org.blackaddons.core.soloclears

import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.*
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.util.TICKS_PER_SECOND
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.backend.gateway.Op
import org.blackaddons.core.backend.gateway.intOrNull
import org.blackaddons.core.backend.gateway.stringOrNull
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.ScoreCalculation
import org.blackaddons.core.log.Log
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.TimeSource

/**
 * Records a solo clear and uploads it.
 * The server starts the session on its own clock.
 * Every few seconds we commit sha256(previous || new bytes), so nobody can fake a run after the fact.
 * Only runs on the client tick. Acks and socket loss come on other threads and get queued.
 */
object RunRecorder {

    /** The server's 299 minimum is its own slack. */
    private const val TARGET_SCORE = 300

    internal const val FIXED_POINT = 32.0

    private const val FINISH_TICKS = 20 * 60

    /** Upload retry delays, well inside the server's 5-minute `SubmitGraceSeconds`. */
    private val RETRY_DELAYS = intArrayOf(5, 20, 60)

    internal enum class Phase { IDLE, ASKING, RECORDING, FINISHING, UPLOADING }

    /** The feature picks the words, this picks the time. */
    internal interface Listener {
        fun onRefused(reason: String)
        fun onAccepted(outcome: RunUpload.Outcome.Accepted)
        fun onSubmissionRefused(outcome: RunUpload.Outcome.Refused)

        /** Separate from [onAbandoned]: the play was fine, but the upload failed. */
        fun onUploadFailed(detail: String, file: String?)

        fun onAbandoned(reason: String)
    }

    @Volatile
    internal var listener: Listener? = null

    /** Nothing happens while false. */
    @Volatile
    var armed: Boolean = false

    // Socket threads read it to decide if they queue.
    @Volatile
    internal var phase = Phase.IDLE
    internal var log: RunLog? = null
    private var session: RunSession? = null
    internal var floor = ""
    internal var startedAt = TimeSource.Monotonic.markNow()
    internal var lastOffset = 0L
    private var ticks = 0
    private var finishTicks = 0
    internal var lastRoom: String? = null

    /** Kept, so a retry sends the same bytes. */
    private var blob: ByteArray? = null
    private var attempts = 0
    private var waitTicks = 0

    /** Written on a worker, read on the tick. */
    @Volatile
    private var outcome: RunUpload.Outcome? = null

    internal val deferred = ArrayList<JsonObject>()

    internal val samples = ArrayList<Sample>(TICKS_PER_SECOND)

    private val inbox = ConcurrentLinkedQueue<Pair<String, JsonObject>>()

    val recording: Boolean get() = phase != Phase.IDLE

    val runId: UUID? get() = session?.runId

    fun start() {
        EventBus.subscribe<DungeonRunStartedEvent> { open() }
        EventBus.subscribe<ClientTickEvent.Pre> { tick() }
        EventBus.subscribe<WorldChangeEvent> { abandon("world") }

        EventBus.subscribe<DungeonRoomStateChangeEvent> {
            record(RunRecords.ROOM) {
                addProperty("room", event.roomName)
                addProperty("from", event.oldState.name)
                addProperty("to", event.newState.name)
            }
        }
        EventBus.subscribe<DungeonDoorOpenedEvent> {
            record(RunRecords.DOOR) {
                addProperty("door", event.type.name)
                event.player?.let { addProperty("opener", it) }
            }
            // Wither doors are segment boundaries too.
            if (event.type == DoorType.WITHER) split("door", "Wither")
        }
        EventBus.subscribe<DungeonKeyPickedUpEvent> {
            record(RunRecords.KEY) {
                addProperty("key", event.type.name)
                addProperty("amount", event.amount)
            }
        }
        EventBus.subscribe<DungeonSecretFoundEvent> {
            record(RunRecords.SECRET) {
                addProperty("room", event.room)
                addProperty("found", event.found)
                addProperty("of", event.total)
                addProperty("x", event.x)
                addProperty("y", event.y)
                addProperty("z", event.z)
            }
        }
        EventBus.subscribe<DungeonSecretUncreditedEvent> {
            record(RunRecords.SECRET_SKIP) {
                addProperty("reason", event.reason)
                addProperty("detail", event.detail)
                event.room?.let { addProperty("room", it) }
                addProperty("found", event.found)
                addProperty("of", event.total)
                addProperty("x", event.x)
                addProperty("y", event.y)
                addProperty("z", event.z)
            }
        }
        EventBus.subscribe<DungeonBonusEarnedEvent> {
            record(RunRecords.BONUS) { addProperty("bonus", event.bonus) }
        }
        EventBus.subscribe<DungeonBossEnterEvent> { record(RunRecords.BOSS) }
        EventBus.subscribe<DungeonPlayerDeathEvent> {
            record(RunRecords.DEATH) { addProperty("reason", event.reason) }
            // Solo: the first death ends the dungeon.
            abandon("death")
        }
    }

    internal fun onFrame(op: String, payload: JsonObject) {
        if (phase == Phase.IDLE) return
        inbox += op to payload
    }

    internal fun onSocketLost() {
        if (phase == Phase.IDLE) return
        inbox += SOCKET_LOST to JsonObject()
    }

    /** The timer starts at Mort's line. The countdown before it would count against the server window. */
    private fun open() {
        if (!armed || phase != Phase.IDLE) return
        if (!LocationUtils.isF7orM7) return
        if (!RunPartySnapshot.looksSolo()) return
        if (!Gateway.connected) {
            BackendNotifier.ensureConnected()
            listener?.onRefused(if (Gateway.unsupported) "unsupported" else "offline")
            return
        }

        floor = LocationUtils.dungeonFloor?.name ?: return
        startedAt = TimeSource.Monotonic.markNow()
        lastOffset = 0L
        phase = Phase.ASKING

        Gateway.say(
            Op.RUN_START,
            JsonObject().apply {
                addProperty("floor", floor)
                DungeonTracker.thePlayer?.dungeonClass?.name?.let { addProperty("class", it) }
                addProperty("mod", Platform.blackaddonsVersion)
                Platform.world.serverBrand?.let { addProperty("brand", it) }
                Platform.world.serverAddress?.let { addProperty("server", it) }
            },
        )
    }

    private fun tick() {
        if (phase == Phase.IDLE) return

        drainInbox()
        if (phase == Phase.IDLE) return

        if (phase != Phase.UPLOADING && !LocationUtils.inDungeon) {
            abandon("left")
            return
        }

        val log = this.log ?: return
        if (log.overflowed) {
            abandon("too_large")
            return
        }

        ticks++

        // No samples after the finish record, because the walk to the chest isn't the run.
        if (phase == Phase.RECORDING) {
            sampleMovement()
            if (ticks % TICKS_PER_SECOND == 0) {
                flushTrack()
                recordScore()
            }
            noteRoom()
        }

        when (phase) {
            Phase.RECORDING -> {
                if (ScoreCalculation.targetScore >= TARGET_SCORE) finish() else pulse()
            }

            // Through finish(), not pulse(), because the commitment we owe is the closing one.
            Phase.FINISHING -> {
                finishTicks++
                if (finishTicks > FINISH_TICKS) abandon("no_socket") else finish()
            }

            Phase.UPLOADING -> uploading()

            else -> Unit
        }
    }

    private fun drainInbox() {
        while (true) {
            val (op, payload) = inbox.poll() ?: return
            when (op) {
                Op.RUN_START_ACK -> onStartAck(payload)
                Op.RUN_ACK -> onAck(payload)
                SOCKET_LOST -> lostSocket()
            }
        }
    }

    private fun lostSocket() {
        // RUN_START or its ack died with the socket. Nothing will answer.
        if (phase == Phase.ASKING) {
            reset()
            listener?.onRefused("offline")
            return
        }
        session?.markLostConnection()
    }

    private fun onStartAck(payload: JsonObject) {
        if (phase != Phase.ASKING) return

        val runId = Uuids.parse(payload.stringOrNull("runId"))
        if (runId == null) {
            // Not fatal, because the socket also carries chat and presence.
            val reason = payload.stringOrNull("error") ?: "unavailable"
            reset()
            listener?.onRefused(reason)
            return
        }

        session = RunSession(runId, payload.intOrNull("pulseSeconds")?.coerceAtLeast(1) ?: DEFAULT_PULSE_SECONDS)
        log = RunLog()
        phase = Phase.RECORDING
        writeHeader(runId)
        deferred.forEach { log?.append(it) }
        deferred.clear()
    }

    private fun onAck(payload: JsonObject) {
        val session = this.session ?: return
        val sequence = payload.intOrNull("seq") ?: return

        when (session.onAck(sequence, payload.stringOrNull("error"))) {
            RunAck.RECORDED -> {
                log?.commit()
                if (session.finished) upload()
            }

            RunAck.IGNORED -> Unit

            // Session expired or chain broken. Nothing after this can be uploaded.
            RunAck.LOST -> abandon(payload.stringOrNull("error") ?: "rejected")
        }
    }

    /** Commits at server cadence, one at a time, never optimistic. A lost frame would break the chain at upload. */
    private fun pulse() {
        val session = this.session ?: return
        val log = this.log ?: return

        if (recovering(session)) return
        if (ticks % (session.pulseSeconds * TICKS_PER_SECOND) != 0) return

        val chunk = log.take() ?: return
        if (!session.commit(chunk, final = false)) log.release()
    }

    /**
     * One link at a time. A commit cut off by a lost socket gets sent again on the next socket (the session is on the
     * server).
     */
    private fun recovering(session: RunSession): Boolean {
        if (!session.awaitingAck) return false
        // No reauth here: it runs every tick and would burn the 10/min auth budget. The supervisor reconnects by itself.
        if (Gateway.connected) session.resendLost() else session.markLostConnection()
        return true
    }

    /** The finish is when the score crosses the threshold. The server window closes on this commitment. */
    private fun finish() {
        val session = this.session ?: return
        val log = this.log ?: return

        if (phase == Phase.RECORDING) {
            // Refused locally, because the backend would only flag it for a moderator anyway.
            if (!RunPartySnapshot.looksSolo()) {
                abandon("not_solo")
                return
            }

            flushTrack()
            if (RunMapSnapshot.available) record(RunRecords.MAP) { RunMapSnapshot.apply(this) }
            record(RunRecords.END) {
                addProperty("score", ScoreCalculation.boardScore)
                addProperty("elapsed", ScoreCalculation.secondsElapsed)
                add("input", RunScoreInput.build(floor))
                add("secretFeed", secretFeed())
                RunPartySnapshot.apply(this)
            }
            phase = Phase.FINISHING
            finishTicks = 0
            // A leftover backoff could be longer than FINISH_TICKS.
            if (!Gateway.connected) Gateway.hurry()
        }

        if (!Gateway.connected) return

        if (recovering(session)) return
        val chunk = log.take() ?: return
        if (!session.commit(chunk, final = true)) log.release()
    }

    private fun upload() {
        val session = this.session ?: return
        val log = this.log ?: return

        phase = Phase.UPLOADING
        blob = log.compressed()
        attempts = 0
        Log.main.debug("Handing in solo clear {} - {} bytes compressed", session.runId, blob?.size)
        attempt()
    }

    /** The worker only publishes the answer. The tick decides. */
    private fun attempt() {
        val session = this.session ?: return
        val blob = this.blob ?: return

        attempts++
        outcome = null
        waitTicks = 0
        RunUpload.submit(session.runId, blob) { answer -> outcome = answer }
    }

    /** Retries within the server's `SubmitGraceSeconds`. One failed POST shouldn't throw away the run. */
    private fun uploading() {
        val answer = outcome
        if (answer == null) {
            if (waitTicks > 0 && --waitTicks == 0) attempt()
            return
        }

        when (answer) {
            is RunUpload.Outcome.Accepted -> {
                SoloClearBoard.invalidate()
                listener?.onAccepted(answer)
                reset()
            }

            // "Already submitted" on a retry means the first attempt landed.
            is RunUpload.Outcome.Refused -> {
                if (attempts > 1 && answer.code == RunUpload.ALREADY_SUBMITTED) {
                    SoloClearBoard.invalidate()
                    listener?.onAccepted(RunUpload.Outcome.Accepted("Submitted", 0, 0))
                } else {
                    listener?.onSubmissionRefused(answer)
                }
                reset()
            }

            else -> retry(answer)
        }
    }

    private fun retry(answer: RunUpload.Outcome) {
        val delay = RETRY_DELAYS.getOrNull(attempts - 1)
        if (delay != null) {
            outcome = null
            waitTicks = delay * TICKS_PER_SECOND
            return
        }

        val kept = blob?.let { bytes -> session?.let { RunUpload.keep(it.runId, bytes) } }
        Log.main.warn("Gave up handing in solo clear {} after {} attempts", session?.runId, attempts)
        listener?.onUploadFailed(describe(answer), kept?.fileName?.toString())
        reset()
    }

    private fun describe(answer: RunUpload.Outcome): String = when (answer) {
        is RunUpload.Outcome.Unexpected -> "HTTP ${answer.status}"
        else -> "no answer"
    }

    private fun abandon(reason: String) {
        if (phase == Phase.IDLE || phase == Phase.UPLOADING) return
        session?.abort()
        val opened = phase != Phase.ASKING
        reset()
        if (opened) listener?.onAbandoned(reason)
    }

    private fun reset() {
        phase = Phase.IDLE
        log = null
        session = null
        floor = ""
        ticks = 0
        finishTicks = 0
        lastRoom = null
        lastOffset = 0L
        blob = null
        attempts = 0
        waitTicks = 0
        outcome = null
        deferred.clear()
        samples.clear()
        inbox.clear()
    }

    private const val DEFAULT_PULSE_SECONDS = 5

    // Not a wire op. Queued, so socket loss gets handled on the tick.
    private const val SOCKET_LOST = "socket_lost"

    internal class Sample(val x: Double, val y: Double, val z: Double, val yaw: Float)
}
