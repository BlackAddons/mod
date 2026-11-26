package org.blackaddons.core.features.impl.dev

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.hudPanel
import org.blackaddons.api.hud.hudText
import org.blackaddons.api.hypixel.Party
import org.blackaddons.api.hypixel.PartyMember
import org.blackaddons.api.hypixel.PartyRole
import org.blackaddons.core.hypixel.CoreParty
import java.awt.Color
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Dev tool: what [Party] holds and how it got there - which channel asks, what made it ask last, and what the server
 * answered. Compare it with `/p list`.
 */
internal object PartyDebugger : Feature(
    description = "Show the party as BlackAddons reads it, with the last request and the server's answer.",
    name = "Party Debugger",
) {
    private val LEADER = Color(255, 200, 80)
    private val MODERATOR = Color(120, 200, 255)
    private val MEMBER = Color(220, 220, 220)
    private val MUTED = Color(255, 255, 255, 150)
    private val ERROR = Color(255, 110, 110)
    private const val TRIGGER_MAX = 48

    @Suppress("unused")
    private val askNow by button("Ask Now", action = { CoreParty.ask("Ask Now button") }, label = "Ask")
        .withDescription("Send a party request right away, then compare the answer with /p list.")

    private class Line(val text: String, val color: Color = Color.WHITE)

    @Suppress("unused")
    private val hud = hudElement("Party Debugger", defaultOffsetX = 5f, defaultOffsetY = 140f) { example ->
        hudPanel(background = Color(0, 0, 0, 110)) {
            for (line in if (example) EXAMPLE else lines()) hudText(line.text, line.color)
        }
    }

    private fun lines(): List<Line> {
        val d = CoreParty.diagnostics
        val members = Party.members
        return buildList {
            add(Line("Source: ${CoreParty.transport?.source ?: "none"}"))
            add(Line(if (members.isEmpty()) "Party: none" else "Party: ${members.size} | You lead: ${Party.isLeader}"))
            members.forEach { add(member(it)) }
            add(Line("Asked: ${ago(d.sentAt)} | Answer: ${ago(d.answeredAt)}", MUTED))
            add(Line("Requests ${d.requests} | Answers ${d.answers} | Refused ${d.refusals}${if (CoreParty.isQueued) " | queued" else ""}", MUTED))
            d.trigger?.let { add(Line("Why: ${it.take(TRIGGER_MAX)} (${ago(d.triggeredAt)})", MUTED)) }
            if (!d.lastSendWentOut) add(Line("Last request not sent: the server does not take it", ERROR))
            d.error?.let { add(Line("Refused: $it", ERROR)) }
        }
    }

    private fun member(member: PartyMember): Line {
        val you = if (member.uuid == Platform.player.uuid) " (you)" else ""
        val name = member.name ?: "${member.uuid.toString().take(8)}... (resolving)"
        val (tag, color) = when (member.role) {
            PartyRole.LEADER -> "LEADER" to LEADER
            PartyRole.MODERATOR -> "MOD" to MODERATOR
            PartyRole.MEMBER -> "MEMBER" to MEMBER
        }
        return Line("  $tag  $name$you", color)
    }

    private fun ago(at: TimeSource.Monotonic.ValueTimeMark?): String =
        at?.let { "%.1fs ago".format(Locale.ROOT, it.elapsedNow().toDouble(DurationUnit.SECONDS)) } ?: "never"

    private val EXAMPLE = listOf(
        Line("Source: own hypixel:party_info"),
        Line("Party: 3 | You lead: true"),
        Line("  LEADER  DreamcastERA (you)", LEADER),
        Line("  MOD  BLACKUM", MODERATOR),
        Line("  MEMBER  1a2b3c4d... (resolving)", MEMBER),
        Line("Asked: 2.4s ago | Answer: 2.3s ago", MUTED),
        Line("Requests 4 | Answers 4 | Refused 0", MUTED),
        Line("Why: chat: BLACKUM joined the party. (2.9s ago)", MUTED),
    )
}
