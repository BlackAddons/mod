package org.blackaddons.core.features.impl.dungeons.simonSays

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.ServerTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.event.world.BlockInteractEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.ServerClock
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.world.BlockHitboxes
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.features.impl.dungeons.simonSays.SimonSays.solution
import org.blackaddons.core.log.Log
import java.awt.Color

/**
 * Legit F7/M7 Simon Says solver. Reads the lantern sequence, highlights buttons in order, tracks clicks and announces
 * breaks. No automation here, `SimonSaysCheats` builds on [solution]. The flow follows NoammAddons, the coordinates are
 * fixed.
 */
object SimonSays : Feature(
    description = "Highlights the F7/M7 Simon Says device solution in order.",
    name = "Simon Says",
) {

    override val section = Categories.Dungeons.Floor7

    private val resetButton by button(
        name = "Reset Solver",
        action = { reset() },
        label = "Reset",
        id = "reset_solver",
    ).withDescription("Forget the pattern read so far and start reading the device from scratch — for when it has gone out of step with what is on the wall.")

    private val blockWrongClicks by boolean("Block Wrong Clicks", default = false)
        .withDescription("Cancel clicks that aren't on the correct button. Sneak to override.")
        .group("Visual")
    private val color1 by color("First Color", Color(0, 255, 0, 160), allowAlpha = true)
        .withDescription("Highlight of the next button.").group("Visual")
    private val color2 by color("Second Color", Color(255, 255, 0, 160), allowAlpha = true)
        .withDescription("Highlight of the button after next.").group("Visual")
    private val color3 by color("Other Color", Color(255, 0, 0, 160), allowAlpha = true)
        .withDescription("Highlight of the remaining buttons.").group("Visual")
    private val highlightWidth by number("Highlight Width", default = 1.0, min = 0.1, max = 2.0, step = 0.05, unit = "x")
        .withDescription("Width of the highlight next to the real button, whatever size its hitbox is enlarged to.").group("Visual")
    private val highlightHeight by number("Highlight Height", default = 1.0, min = 0.1, max = 2.0, step = 0.05, unit = "x")
        .withDescription("Height of the highlight next to the real button, whatever size its hitbox is enlarged to.").group("Visual")
    private val showWhilePlaying by boolean("Show While Pattern Plays", default = true)
        .withDescription("Keep the highlights up while the device plays its pattern and the buttons are gone.").group("Visual")

    private val alertsEnabled by boolean("Alerts Enabled", default = true).group("Alerts")
    private val breakAlert by boolean("SS Break Alert", default = true)
        .withDescription("Announce in party chat when the device resets.").showIf { alertsEnabled.value }.group("Alerts")
    private val restartChat by boolean("Send Restart Chat", default = true)
        .withDescription("Announce in party chat when the device restarts.").showIf { alertsEnabled.value }.group("Alerts")
    private val alertSound by boolean("Alert Sound", default = true)
        .withDescription("Play a sound when the device breaks.").showIf { alertsEnabled.value }.group("Alerts")
    private val showTitle by boolean("Show Title", default = true)
        .withDescription("Show a title when the device breaks / restarts.").showIf { alertsEnabled.value }.group("Alerts")
    private val printSsTime by boolean("Print SS Time In Chat", default = true)
        .withDescription("Show how long Simon Says took when a player at the device completes it.")
        .showIf { alertsEnabled.value }.group("Alerts")

    private val logger = Log.area("simon-says")
    private const val BREAK_TICKS = 12
    private const val SLOW_HANDBACK_TICKS = 60
    private const val FULL_SKIP_LANTERNS = 3

    private val device = SimonSaysDevice
    private val deviceCenter = BlockPos3(110, 121, 93).center()

    private val deviceRegex = Regex("""(.+) (activated|completed) a (terminal|device|lever)! \((\d)/(\d)\)""")

    /**
     * The pattern of the whole round (lanterns at x = 111). Kept, not consumed: each stage replays it plus one, and
     * that's how we tell a replayed lantern from a new one.
     */
    private val pattern = ArrayList<BlockPos3>()

    private var pressed = 0

    /** A lantern counts once, when it turns on. */
    private val litLastTick = HashSet<BlockPos3>()

    /** Still to press, in order. Used by the highlight and the autosolver. */
    val solution: List<BlockPos3> get() = if (pressed >= pattern.size) emptyList() else pattern.drop(pressed)

    /** An autosolver follows it while the pattern plays. */
    var lastLit: BlockPos3? = null
        private set

    var shownThisStage = 0
        private set

    /**
     * Buttons go away while the device plays and come back for input. Exact and timing-free, lanterns can't give us
     * that.
     */
    val isPlayingPattern: Boolean
        get() = blockAt(device.BUTTON_CHECK) != device.STONE_BUTTON

    /** From a break until it lights up again. Tracked with alerts off too, because the autosolver restarts on it. */
    val isBroken: Boolean get() = wasBroken

    private var firstStage = true

    private var skipTrimmed = false

    /** More than one press before the first lantern is a skip attempt. */
    private var startPresses = 0

    /**
     * The first lantern can be a skip decoy (start pressed twice, first stage still playing). Only a third lantern
     * (full skip) or the end of the show (partial skip, all real) decides.
     */
    val mayBeDecoy: Boolean
        get() = firstStage && !skipTrimmed && startPresses > 1 && pattern.size < FULL_SKIP_LANTERNS && isPlayingPattern

    private var listening = false
    private var thingsDone = 0
    private val serverClock = ServerClock()
    private var breakAt = serverClock.after(BREAK_TICKS)
    private var slowBreakAt = serverClock.after(SLOW_HANDBACK_TICKS)
    private var quietSinceMs = 0L
    private var canBreak = false
    private var askedForInput = false
    private var lastLitCount = 0
    private var lastButtonsUp = false
    private var wallTicks = 0
    private var wallSinceMs = 0L
    private var wasBroken = false
    private val chatTimer = SimonSaysChatTimer()
    private var nearbyPlayers = emptySet<String>()

    override fun init() {
        register<WorldChangeEvent> { resetSolver(); resetAlerts() }
        register<ClientTickEvent.Post> { onTick() }
        register<ServerTickEvent> { trackBreaks() }
        register<RenderWorldEvent> { onRender() }
        register<BlockInteractEvent> { handleClick(event) }
        // From the packet, so chat-hiding mods can't blind it.
        register<ChatReceiveEvent> { onChat(event.unformatted) }
    }

    override fun onDisable() {
        resetSolver()
        resetAlerts()
    }

    fun reset() {
        beginRound()
        resetAlerts()
    }

    private fun blockAt(pos: BlockPos3): String? = Platform.world.blockIdAt(pos.x, pos.y, pos.z)

    private fun onTick() {
        if (LocationUtils.f7Phase != F7Phase.GOLDOR) {
            if (pattern.isNotEmpty()) beginRound()
            chatTimer.reset()
            nearbyPlayers = emptySet()
            return
        }

        if (chatTimer.started) nearbyPlayers = playersAtDevice()

        // Edge-triggered, because each lantern stays on for a few ticks.
        for (pos in device.lanterns) {
            if (blockAt(pos) == device.SEA_LANTERN) {
                if (litLastTick.add(pos)) onLanternLit(pos)
            } else {
                litLastTick.remove(pos)
            }
        }
    }

    private fun onLanternLit(pos: BlockPos3) {
        lastLit = pos
        // Nothing left to press and a lantern turns on: the replay started.
        if (pattern.isNotEmpty() && pressed >= pattern.size) {
            pressed = 0
            shownThisStage = 0
            // A replay means stage 2+. Keyed off the device, so the decoy rule can't eat later stages if nobody presses.
            firstStage = false
        }
        shownThisStage++
        if (pos in pattern) return

        pattern.add(pos)
        trimSkippedDecoy()
    }

    /**
     * A fully skipped first stage turns on three lanterns, and the first one is a decoy (pressing it fails anyway). A
     * partial skip turns on two, both real. Only drops the first decoy, once.
     */
    private fun trimSkippedDecoy() {
        if (!firstStage || skipTrimmed || pattern.size < FULL_SKIP_LANTERNS) return
        // From the pattern itself, so later stages lose it too.
        pattern.removeAt(0)
        skipTrimmed = true
    }

    private fun beginRound() {
        pattern.clear()
        litLastTick.clear()
        pressed = 0
        shownThisStage = 0
        lastLit = null
        firstStage = true
        skipTrimmed = false
        startPresses = 0
    }

    private fun handleClick(event: BlockInteractEvent) {
        if (LocationUtils.f7Phase != F7Phase.GOLDOR) return
        val clicked = BlockPos3(event.x, event.y, event.z)

        /*
         * Rounds only start on the start button. Buttons come back every stage, so a reset keyed off them would throw
         * away the pattern we just read.
         */
        if (clicked == device.START_BUTTON) {
            val presses = if (pattern.isEmpty()) startPresses + 1 else 1
            beginRound()
            startPresses = presses
            return
        }
        if (blockAt(clicked) != device.STONE_BUTTON) return

        // The button at x=110 belongs to the lantern at x=111.
        val check = BlockPos3(clicked.x + 1, clicked.y, clicked.z)
        val expected = pattern.getOrNull(pressed) ?: return

        if (check == expected) {
            pressed++
            return
        }

        if (blockWrongClicks.value && !Platform.player.isSneaking) {
            event.cancelled = true
            return
        }
        // One ahead: count the skipped one too.
        if (pattern.getOrNull(pressed + 1) == check) pressed += 2
    }

    private fun onChat(message: String) {
        /*
         * Not gated on alertsEnabled: this arms `listening`, which gates trackBreaks and `isBroken`. With the gate,
         * AutoSS restart-on-break broke.
         */
        if (LocationUtils.f7Phase != F7Phase.GOLDOR) return
        if (device.GOLDOR_INTRO.matches(message)) {
            resetAlerts()
            listening = true
            chatTimer.start(DungeonTracker.currentTime)
            return
        }
        val match = deviceRegex.find(message) ?: return
        val name = match.groupValues[1]
        val type = match.groupValues[3]
        val completed = match.groupValues[4].toIntOrNull() ?: 0
        if (type == "device" && match.groupValues[2] == "completed") {
            val seconds = chatTimer.complete(DungeonTracker.currentTime, name, playersAtDevice() + nearbyPlayers)
            if (seconds != null && alertsEnabled.value && printSsTime.value) {
                val duration = seconds.formatDecimals()
                Platform.chat.local(ChatMessage.build {
                    brand()
                    append(I18n.tr("blackaddons.simonsays.completed_time", duration), Brand.OK)
                })
            }
        }
        if (!listening) return
        when (type) {
            "terminal", "lever" -> thingsDone++
            "device" -> if (thingsDone + 1 >= completed) {
                listening = false
                canBreak = false
                wasBroken = false
            }
        }
    }

    // A break is a dark wall with no buttons for a window of server time. Tracked with alerts off too.
    private fun trackBreaks() {
        if (!listening) return
        serverClock.tick()
        wallTicks++
        val lit = device.lanterns.count { blockAt(it) != device.OBSIDIAN }
        val buttonsUp = device.buttons.any { blockAt(it) != device.AIR }
        logWallChange(lit, buttonsUp)

        if (lit > 0) {
            restartWindow()
            canBreak = true
            askedForInput = false
            if (wasBroken) {
                wasBroken = false
                if (alertsEnabled.value && restartChat.value) Platform.chat.sendCommand("pc SS Started Again!")
                if (alertsEnabled.value && showTitle.value) Platform.feedback.showTitle("§a§l§nSS Started!")
            }
            return
        }

        // The window starts when the buttons go away, not at the last lantern, or a long input phase eats it up.
        if (buttonsUp) {
            restartWindow()
            askedForInput = true
            return
        }

        if (!canBreak) return
        // Dark after a lantern is ambiguous (slow P3Sim handback, flash of a failed press), so it waits longer.
        if (!serverClock.passed(if (askedForInput) breakAt else slowBreakAt)) return

        canBreak = false
        wasBroken = true
        logger.debug(
            "SS break: dark, no buttons for {} ms after {} (pattern {}, pressed {})",
            System.currentTimeMillis() - quietSinceMs, if (askedForInput) "input" else "a lantern", pattern.size, pressed,
        )
        // The device restarts from zero. An autosolver shouldn't wait on old state.
        beginRound()
        if (!alertsEnabled.value) return
        if (breakAlert.value) Platform.chat.sendCommand("pc SS Broke!")
        if (alertSound.value) Platform.feedback.playSound("minecraft:block.anvil.land", 5f, 0f)
        if (showTitle.value) Platform.feedback.showTitle("§c§l§nSS BROKE!")
    }

    private fun restartWindow() {
        breakAt = serverClock.after(BREAK_TICKS)
        slowBreakAt = serverClock.after(SLOW_HANDBACK_TICKS)
        quietSinceMs = System.currentTimeMillis()
    }

    private fun logWallChange(lit: Int, buttonsUp: Boolean) {
        if (lit == lastLitCount && buttonsUp == lastButtonsUp) return
        logger.debug(
            "SS wall: lit {} -> {}, buttons {} -> {} after {} server ticks / {} ms (pattern {}, pressed {})",
            lastLitCount, lit, lastButtonsUp, buttonsUp, wallTicks, System.currentTimeMillis() - wallSinceMs,
            pattern.size, pressed,
        )
        lastLitCount = lit
        lastButtonsUp = buttonsUp
        wallTicks = 0
        wallSinceMs = System.currentTimeMillis()
    }

    private fun onRender() {
        val remaining = solution
        if (LocationUtils.f7Phase != F7Phase.GOLDOR || remaining.isEmpty()) return
        for (i in remaining.indices) {
            val color = when (i) {
                0 -> color1.value
                1 -> color2.value
                else -> color3.value
            }
            val b = device.buttonFor(remaining[i])
            BlockHitboxes.vanilla { Platform.world.blockOutlineBoxes(b.x, b.y, b.z) }
                .ifEmpty { if (showWhilePlaying.value) listOf(device.plateBox(b)) else emptyList() }
                .forEach { real ->
                    val box = real.scaledFace(highlightWidth.value, highlightHeight.value)
                    Platform.worldRenderer.filledBox(
                        box.min.x, box.min.y, box.min.z,
                        box.max.x, box.max.y, box.max.z,
                        color, true,
                    )
                }
        }
    }

    private fun resetSolver() = beginRound()


    private fun resetAlerts() {
        listening = false
        thingsDone = 0
        restartWindow()
        wallTicks = 0
        wallSinceMs = quietSinceMs
        canBreak = false
        askedForInput = false
        wasBroken = false
        chatTimer.reset()
        nearbyPlayers = emptySet()
    }

    private fun playersAtDevice(): Set<String> =
        Platform.world.entitiesNear(deviceCenter.x, deviceCenter.y, deviceCenter.z, 5.0)
            .filter { it.isPlayer && deviceCenter.distanceSqr(Vec3d(it.x, it.y, it.z)) <= 25.0 }
            .mapTo(HashSet()) { it.name }
}

internal class SimonSaysChatTimer {
    private var startTick: Long? = null
    val started: Boolean get() = startTick != null

    fun start(tick: Long) { startTick = tick }

    fun reset() { startTick = null }

    fun complete(tick: Long, player: String, nearbyPlayers: Collection<String>): Double? {
        val startedAt = startTick ?: return null
        if (nearbyPlayers.none { it.equals(player, ignoreCase = true) }) return null
        startTick = null
        return (tick - startedAt).coerceAtLeast(0) / 20.0
    }
}
