package org.blackaddons.core.soloclears

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.io.array
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.long
import org.blackaddons.api.util.io.str
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http

/** Server positions. A revoked run moves everyone below it. */
internal data class BoardEntry(
    val position: Int,
    val runId: String,
    val name: String?,
    val elapsedSeconds: Int,
    val clientElapsedMs: Long,
    val score: Int,
    val dungeonClass: String?,
)

/** Three boards per floor. A run is on Overall and on exactly one half. */
internal data class Board(val floor: String, val category: String, val entries: List<BoardEntry>)

internal data class MyRun(
    val id: String,
    val floor: String,
    val status: String,
    val statusReason: String?,
    val elapsedSeconds: Int,
    val score: Int,
    val claimedScore: Int,
    val skippedPuzzles: List<String>,
    val flags: List<String>,
)

/** Floor boards are public. Own runs need the session. Blocking. */
internal object SoloClearBoard {

    /** The server refuses other floors anyway. */
    val FLOORS = listOf("F7", "M7")

    const val OVERALL = "Overall"
    const val NO_SKIPS = "NoSkips"
    const val WITH_SKIPS = "WithSkips"

    val CATEGORIES = listOf(OVERALL, NO_SKIPS, WITH_SKIPS)

    /** Matches the board's 30s `Cache-Control`. */
    private const val CACHE_MILLIS = 30_000L

    private val cached = HashMap<String, Cached>()

    fun board(floor: String, category: String): Board? {
        val key = "$floor/$category"
        synchronized(cached) {
            cached[key]?.takeIf { System.currentTimeMillis() - it.at < CACHE_MILLIS }?.let { return it.board }
        }

        val response = Platform.http.getBlocking("${Backend.API_V1}/soloclears/leaderboard/$floor?category=$category")
            ?.takeIf { it.status == 200 } ?: return null
        val json = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull() ?: return null

        val board = Board(
            floor = json.str("floor") ?: floor,
            category = json.str("category") ?: category,
            entries = json.array("entries")
                ?.mapNotNull { element -> (element as? JsonObject)?.let(::entry) }
                ?: emptyList(),
        )
        synchronized(cached) { cached[key] = Cached(board, System.currentTimeMillis()) }
        return board
    }

    fun mine(page: Int = 1, pageSize: Int = 25): List<MyRun>? {
        val response = Http.get(
            "${Backend.API_V1}/me/soloclears?page=$page&pageSize=$pageSize",
            authenticated = true,
        )?.takeIf { it.status == 200 } ?: return null
        val json = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull() ?: return null

        return json.array("items")?.mapNotNull { item -> (item as? JsonObject)?.let(::run) } ?: emptyList()
    }

    fun invalidate() {
        synchronized(cached) { cached.clear() }
    }

    private fun entry(json: JsonObject) = BoardEntry(
        position = json.int("position"),
        runId = json.str("runId").orEmpty(),
        name = json.str("name"),
        elapsedSeconds = json.int("elapsedSeconds"),
        clientElapsedMs = json.long("clientElapsedMs"),
        score = json.int("score"),
        dungeonClass = json.str("dungeonClass"),
    )

    private fun run(json: JsonObject) = MyRun(
        id = json.str("id").orEmpty(),
        floor = json.str("floor").orEmpty(),
        status = json.str("status").orEmpty(),
        statusReason = json.str("statusReason"),
        elapsedSeconds = json.int("elapsedSeconds"),
        score = (json.get("score") as? JsonObject).int("total"),
        claimedScore = json.int("claimedScore"),
        skippedPuzzles = json.array("skippedPuzzles")?.mapNotNull { it.asStringOrNull() } ?: emptyList(),
        flags = json.array("flags")?.mapNotNull { flag ->
            (flag as? JsonObject)?.let { "${it.str("rule")}: ${it.str("detail")}" }
        } ?: emptyList(),
    )

    private fun JsonElement.asStringOrNull(): String? =
        takeIf { it.isJsonPrimitive }?.asString

    private class Cached(val board: Board, val at: Long)
}
