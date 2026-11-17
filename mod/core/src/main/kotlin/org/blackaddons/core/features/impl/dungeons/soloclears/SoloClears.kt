package org.blackaddons.core.features.impl.dungeons.soloclears

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.hud.hudChromaText
import org.blackaddons.api.hud.hudPanel
import org.blackaddons.api.util.I18n
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.features.impl.dungeons.soloclears.SoloClears.target
import org.blackaddons.core.soloclears.RunRecorder
import org.blackaddons.core.soloclears.RunUpload
import java.awt.Color

/**
 * Records solo F7/M7 runs for the leaderboard. The board never counts the mayor perk and forgives quiz and ice fill.
 * The backend puts runs on three boards by what was skipped, so [target] picks the board the player aims for, and the
 * number on screen follows it.
 */
object SoloClears : Feature(
    description = "Records a solo F7/M7 for the BlackAddons leaderboard and hands it in at 300.",
    name = "Solo Clears",
) {

    override val section = Categories.Dungeons.Floor7

    private const val TARGET_ANY = "Skips allowed"
    private const val TARGET_NO_SKIPS = "No skips"

    private val target by mode("Board", TARGET_ANY, listOf(TARGET_ANY, TARGET_NO_SKIPS))
        .withDescription(
            "Which board to aim at. Skips allowed does not charge for the quiz and ice fill, the way the " +
                "board scores them; No skips keeps their ten points, because a run that skipped one is " +
                "ranked among the runs that did.",
        )
        .group("Run")

    private val announce by boolean("Announce In Chat", true)
        .withDescription("Say in chat when a recording opens, and what the server made of it afterwards.")
        .group("Run")

    private val showIndicator by boolean("Indicator", true)
        .withDescription("Show a marker while this mode is on, so the score on screen is never unexplained.")
        .group("Indicator")

    private val indicatorColor by color("Indicator Color", Color(0xB4, 0x6D, 0xF2), allowAlpha = true, textChroma = true)
        .withDescription("Color of the indicator text.")
        .showIf { showIndicator.value }
        .group("Indicator")

    private val indicatorBackground by color("Indicator Background", Color(0, 0, 0, 110), allowAlpha = true)
        .withDescription("Background fill behind the indicator.")
        .showIf { showIndicator.value || showTimer.value }
        .group("Indicator")

    private val showTimer by boolean("Timer", true)
        .withDescription("Display RTA, IGT and Lag timers for solo dungeon clears.")
        .group("Timer")

    private val rtaColor by color("RTA Color", Color(0x55, 0xFF, 0xFF), allowAlpha = true, textChroma = true)
        .withDescription("Color of the RTA timer text.")
        .showIf { showTimer.value }
        .group("Timer")

    private val igtColor by color("IGT Color", Color(0x55, 0xFF, 0x55), allowAlpha = true, textChroma = true)
        .withDescription("Color of the IGT timer text.")
        .showIf { showTimer.value }
        .group("Timer")

    private val lagColor by color("Lag Color", Color.WHITE, allowAlpha = true, textChroma = true)
        .withDescription("Color of the Lag timer text.")
        .showIf { showTimer.value }
        .group("Timer")

    val forgivesSkips: Boolean get() = target.value != TARGET_NO_SKIPS

    /**
     * Shows that the score is the board's, not the game's. Its own HUD element, because the old map badge was too easy
     * to switch off by accident. The dot shows if a run is open. The text stays the same.
     */
    @Suppress("unused")
    private val indicator = hudElement(
        name = "Solo Clear",
        enabled = { showIndicator.value || showTimer.value },
        shouldDraw = { RunRecorder.recording || SoloClearTimer.shouldRender },
        defaultAnchorX = AnchorX.RIGHT,
        defaultAnchorY = AnchorY.TOP,
        defaultOffsetX = -5f,
        defaultOffsetY = 100f,
    ) { example ->
        hudPanel(background = indicatorBackground.value, radius = 3f) {
            if (showIndicator.value) {
                hudChromaText(label(example), indicatorColor)
            }
            if (showTimer.value) {
                val (rta, igt, lag) = if (example) {
                    Triple(
                        SoloClearTimer.PREVIEW_RTA,
                        SoloClearTimer.PREVIEW_IGT,
                        SoloClearTimer.PREVIEW_LAG,
                    )
                } else {
                    Triple(
                        SoloClearTimer.PREFIX_RTA + SoloClearTimer.formatTimeWithMinutes(SoloClearTimer.rtaMs),
                        SoloClearTimer.PREFIX_IGT + SoloClearTimer.formatTimeWithMinutes(SoloClearTimer.igtMs),
                        SoloClearTimer.PREFIX_LAG + SoloClearTimer.formatDesync(SoloClearTimer.desyncMs),
                    )
                }
                hudChromaText(rta, rtaColor)
                hudChromaText(igt, igtColor)
                hudChromaText(lag, lagColor)
            }
        }
    }

    private fun label(example: Boolean): String {
        val live = example || RunRecorder.recording || SoloClearTimer.running || SoloClearTimer.completed
        val mark = if (live) "●" else "○"
        return "$mark ${I18n.trOr("blackaddons.soloclears.indicator", "Solo Clear mode")}"
    }

    override fun init() {
        RunRecorder.listener = Voice
        SoloClearTimer.init()
    }

    override fun onEnable() {
        RunRecorder.armed = true
    }

    override fun onDisable() {
        RunRecorder.armed = false
        SoloClearTimer.reset()
    }

    /** Recorder messages in chat. Players reread refusals, and the backend explains things we don't know. */
    private object Voice : RunRecorder.Listener {

        override fun onRefused(reason: String) =
            say("blackaddons.soloclears.refused", Brand.FAIL, reason(reason))

        override fun onAccepted(outcome: RunUpload.Outcome.Accepted) =
            say("blackaddons.soloclears.accepted", Brand.OK, time(outcome.elapsedSeconds), outcome.score)

        override fun onSubmissionRefused(outcome: RunUpload.Outcome.Refused) {
            val detail = outcome.detail ?: reason(outcome.code)
            say("blackaddons.soloclears.rejected", Brand.FAIL, detail)
        }

        /** The run was fine but the upload failed. Worded differently from an abandoned recording. */
        override fun onUploadFailed(detail: String, file: String?) {
            say("blackaddons.soloclears.upload_failed", Brand.FAIL, detail)
            if (file != null) say("blackaddons.soloclears.upload_kept", Brand.BODY, file)
        }

        override fun onAbandoned(reason: String) =
            say("blackaddons.soloclears.abandoned", Brand.BODY, reason(reason))

        private fun reason(code: String): String = I18n.trOr("blackaddons.soloclears.reason.$code", code)

        private fun time(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

        private fun say(key: String, color: Color, vararg args: Any) {
            if (!announce.value) return
            Platform.chat.local(ChatMessage.build {
                brand()
                append(I18n.tr(key, *args), color)
            })
        }
    }
}
