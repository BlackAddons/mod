package org.blackaddons.platform.features.impl.dev

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam
import org.blackaddons.api.Platform
import org.blackaddons.api.event.chat.ActionBarEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.dev.DevFiles
import java.awt.Color
import java.time.LocalDateTime

/**
 * Dev: a key dumps the raw scoreboard, tab list, action bar or chat to `<configDir>/dev/dumps/` and the
 * clipboard. `§` and non-ASCII become `\uXXXX` so hidden formatting and team prefix/suffix structure show.
 * The action bar is captured from [ActionBarEvent] while enabled.
 */
object Dumpers : Feature(
    description = "Dump the scoreboard, tab list, action bar, or chat to a file and the clipboard.",
    name = "Dumpers",
) {
    private val scoreboardKey by keybind("Dump Scoreboard")
        .withDescription("Press in-world to dump the sidebar scoreboard.")
    private val tabKey by keybind("Dump Tab List")
        .withDescription("Press to dump the player-list (tab) entries.")
    private val actionBarKey by keybind("Dump Action Bar")
        .withDescription("Press to dump the last action-bar message.")
    private val chatKey by keybind("Dump Chat")
        .withDescription("Press to dump the last few chat messages, spans and hover text included.")

    private val GREEN = Color(0x55FF55)
    private val YELLOW = Color(0xFFAA00)
    private val SECTION_SPLIT = Regex(" {3,}")

    /** A whole /f list page plus context. */
    private const val CHAT_KEPT = 40

    private val mc get() = Minecraft.getInstance()

    private var lastActionBarRaw: String? = null
    private var lastActionBarUnformatted: String? = null

    private val recentChat = ArrayDeque<ChatReceiveEvent>()

    override fun init() {
        register<ActionBarEvent> {
            lastActionBarRaw = event.raw
            lastActionBarUnformatted = event.unformatted
        }
        // From the packet, so messages other mods hide still get captured.
        register<ChatReceiveEvent> {
            recentChat.addLast(event)
            while (recentChat.size > CHAT_KEPT) recentChat.removeFirst()
        }

        onKeybind(scoreboardKey) { emit("scoreboard", buildScoreboardDump()) }
        onKeybind(tabKey) { emit("tab", buildTabDump()) }
        onKeybind(actionBarKey) { emit("actionbar", buildActionBarDump()) }
        onKeybind(chatKey) { emit("chat", buildChatDump()) }
    }

    /** A null dump isn't written: it used to overwrite the clipboard with "no chat captured yet" and report success. */
    private fun emit(kind: String, content: String?) {
        if (content == null) {
            Platform.chat.local(
                I18n.trOr("blackaddons.dumpers.empty", "Nothing to dump for %s yet — the clipboard is untouched.", kind),
                YELLOW,
            )
            return
        }
        Platform.clipboard.copy(content)
        val file = runCatching { DevFiles.writeTimestamped("dumps", kind, "txt", content) }.getOrNull()
        if (file != null) {
            Platform.chat.local(
                I18n.trOr(
                    "blackaddons.dumpers.saved",
                    "Dumped %s -> dev/dumps/%s (also on the clipboard).",
                    kind, file.fileName,
                ),
                GREEN,
            )
        } else {
            Platform.chat.local(
                I18n.trOr(
                    "blackaddons.dumpers.copied",
                    "Copied %s dump to the clipboard (file write failed).",
                    kind,
                ),
                GREEN,
            )
        }
    }

    private fun buildScoreboardDump(): String? {
        val scoreboard = mc.level?.scoreboard ?: return null
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null

        val out = StringBuilder()
        out.appendLine("=== scoreboard sidebar dump @ ${LocalDateTime.now()} ===")
        out.appendLine("objective.name        = ${objective.name}")
        out.appendLine("objective.displayName = ${esc(objective.displayName.string)}")
        out.appendLine("---- lines (score desc) ----")
        scoreboard.listPlayerScores(objective)
            .sortedByDescending { it.value() }
            .forEachIndexed { i, entry ->
                val owner = entry.owner()
                val ownerName = entry.ownerName()
                val team = scoreboard.getPlayersTeam(owner)
                val assembled = if (team == null) ownerName else PlayerTeam.formatNameForTeam(team, ownerName)
                out.appendLine("[$i] score=${entry.value()}")
                out.appendLine("    owner()     = ${esc(owner)}")
                out.appendLine("    ownerName() = ${esc(ownerName.string)}")
                if (team != null) {
                    out.appendLine("    team.name   = ${team.name}")
                    out.appendLine("    team.prefix = ${esc(team.playerPrefix.string)}")
                    out.appendLine("    team.suffix = ${esc(team.playerSuffix.string)}")
                } else {
                    out.appendLine("    team        = <none>")
                }
                out.appendLine("    assembled   = ${esc(assembled.string)}")
            }
        return out.toString()
    }

    private fun buildTabDump(): String? {
        val connection = mc.player?.connection ?: return null
        val players = connection.onlinePlayers
        val out = StringBuilder()
        out.appendLine("=== tab list dump @ ${LocalDateTime.now()} ===")
        out.appendLine("players = ${players.size}")
        out.appendLine("---- entries ----")
        players.forEachIndexed { i, info ->
            val display = info.tabListDisplayName?.string
            out.appendLine("[$i] ${esc(info.profile.name)} latency=${info.latency}")
            out.appendLine("    display = ${if (display != null) esc(display) else "<none>"}")
        }
        return out.toString()
    }

    /** Per span: Hypixel sends `/f list` as one multi-line message and hides nicked players' real names in hovers. */
    private fun buildChatDump(): String? {
        if (recentChat.isEmpty()) return null
        val out = StringBuilder()
        out.appendLine("=== chat dump @ ${LocalDateTime.now()} ===")
        out.appendLine("messages = ${recentChat.size} (oldest first)")
        recentChat.forEachIndexed { i, message ->
            out.appendLine("---- [$i] type=${message.type} ----")
            out.appendLine("raw = ${esc(message.raw)}")
            message.message.spans.forEachIndexed { j, span ->
                out.appendLine("  span[$j] = ${esc(span.text)}")
                span.hover?.let { out.appendLine("           hover = ${esc(it)}") }
                span.click?.let { out.appendLine("           click = $it") }
            }
        }
        return out.toString()
    }

    private fun buildActionBarDump(): String? {
        val raw = lastActionBarRaw ?: return null
        val out = StringBuilder()
        out.appendLine("=== action bar dump @ ${LocalDateTime.now()} ===")
        out.appendLine("raw         = ${esc(raw)}")
        out.appendLine("unformatted = ${esc(lastActionBarUnformatted ?: "")}")
        out.appendLine("---- sections (split on 3+ spaces) ----")
        raw.split(SECTION_SPLIT).forEachIndexed { i, s -> out.appendLine("[$i] ${esc(s)}") }
        return out.toString()
    }

    // Quoted, with § and non-printable/non-ASCII as \uXXXX.
    private fun esc(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '§' -> append("\\u00a7")
            c == '\n' -> append("\\n")
            c.code in 0x20..0x7e -> append(c)
            else -> append("\\u%04x".format(c.code))
        }
        append('"')
    }
}
