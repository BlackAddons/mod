package org.blackaddons.core.soloclears

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.ScoreCalculation
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.soloclears.RunRecorder.Phase
import org.blackaddons.core.soloclears.RunRecorder.Sample
import java.util.UUID
import kotlin.math.roundToInt

/* What RunRecorder writes into a run: the header, events, score, splits and the movement track. */

internal fun RunRecorder.writeHeader(runId: UUID) {
    val header = JsonObject().apply {
        addProperty("t", RunRecords.HEADER)
        addProperty("ms", 0L)
        addProperty("v", 1)
        addProperty("runId", runId.toString())
        addProperty("floor", floor)
        DungeonTracker.thePlayer?.dungeonClass?.name?.let { addProperty("class", it) }
        addProperty("mod", Platform.blackaddonsVersion)
        addProperty("mc", Platform.minecraftVersion)
        Platform.world.serverBrand?.let { addProperty("brand", it) }
        Platform.world.serverAddress?.let { addProperty("server", it) }
        RunPartySnapshot.apply(this)
    }
    log?.append(header)
}

internal fun RunRecorder.secretFeed(): JsonObject {
    val feed = ScoreCalculation.secretFeed
    return JsonObject().apply {
        addProperty("actionBars", feed.actionBars)
        addProperty("withSecrets", feed.withSecrets)
        addProperty("credited", feed.credited)
        add("skipped", JsonObject().apply { feed.skipped.forEach { (reason, count) -> addProperty(reason, count) } })
    }
}

/** Held until a run exists. Offsets never decrease, because the backend rejects files whose clock goes back. */
internal fun RunRecorder.record(type: String, build: JsonObject.() -> Unit = {}): Boolean {
    if (phase == Phase.IDLE || phase == Phase.UPLOADING) return false

    val record = JsonObject().apply {
        addProperty("t", type)
        addProperty("ms", offset())
        build()
    }
    val log = this.log
    if (log == null) deferred += record else log.append(record)
    return true
}

internal fun RunRecorder.offset(): Long {
    val elapsed = (System.nanoTime() - startedAtNanos) / 1_000_000L
    lastOffset = maxOf(lastOffset, elapsed)
    return lastOffset
}

/** The board's five score components, because the finish is judged on the board total. */
internal fun RunRecorder.recordScore() {
    val parts = ScoreCalculation.board
    record(RunRecords.SCORE) {
        addProperty("secrets", parts.secrets)
        addProperty("rooms", parts.rooms)
        addProperty("skill", parts.skill)
        addProperty("speed", parts.speed)
        addProperty("bonus", parts.bonus)
        addProperty("total", parts.total)
        addProperty("elapsed", ScoreCalculation.secondsElapsed)
    }
}

internal fun RunRecorder.noteRoom() {
    val room = MapState.getRoomAt(Platform.player.x, Platform.player.z)?.name ?: return
    if (room == lastRoom) return
    lastRoom = room
    split("room", room)
}

internal fun RunRecorder.split(at: String, label: String?) {
    record(RunRecords.SPLIT) {
        addProperty("at", at)
        label?.let { addProperty("label", it) }
    }
}

internal fun RunRecorder.sampleMovement() {
    samples += Sample(Platform.player.x, Platform.player.y, Platform.player.z, Platform.player.yaw)
}

/** One anchor plus deltas per tick `[dx, dy, dz, ...]` in 1/32 block, batched per second. */
internal fun RunRecorder.flushTrack() {
    if (samples.isEmpty()) return
    val anchor = samples.first()

    val deltas = JsonArray()
    for (index in 1 until samples.size) {
        val from = samples[index - 1]
        val to = samples[index]
        deltas.add(((to.x - from.x) * FIXED_POINT).roundToInt())
        deltas.add(((to.y - from.y) * FIXED_POINT).roundToInt())
        deltas.add(((to.z - from.z) * FIXED_POINT).roundToInt())
    }
    samples.clear()

    record(RunRecords.TRACK) {
        addProperty("x", anchor.x)
        addProperty("y", anchor.y)
        addProperty("z", anchor.z)
        addProperty("yaw", anchor.yaw)
        if (deltas.size() > 0) add("d", deltas)
    }
}
