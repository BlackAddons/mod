package org.blackaddons.cheat.features.impl.cheats.simonSays

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.feature.settings.impl.BooleanSetting
import org.blackaddons.api.player.HandBack
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.cheat.aim.AimCurve
import org.blackaddons.cheat.features.impl.cheats.simonSays.SimonSaysCheats.panic
import org.blackaddons.core.features.impl.dungeons.simonSays.SimonSays
import org.blackaddons.core.features.impl.dungeons.simonSays.SimonSaysDevice
import java.awt.Color

/**
 * AutoSS inside [SimonSays]: auto-solve or triggerbot. It's an extension, not a feature: with [SimonSays] off there's
 * no solution to act on. Clicks feed the legit solver's handler, which removes the solved button.
 */
// BLACKUM: Holy safety nightmare
object SimonSaysCheats : FeatureExtension(SimonSays, group = "Auto Solve") {

    private val autoSolve: BooleanSetting by exclusiveBoolean("Auto Solve") { triggerbot }
        .withDescription("Rotate to and click the buttons for you. Auto-start and rotation settings apply only to this mode.")
    private val triggerbot: BooleanSetting by exclusiveBoolean("AutoSS Triggerbot") { autoSolve }
        .withDescription("Click the next correct button when you aim at it, without rotating. Cannot run with Auto Solve.")
    private val noRotate by boolean("No Rotate", default = false)
        .withDescription("Aim using server-side rotation while your camera stays put.")
        .showIf { autoSolve.value }
    private val smoothReturn by boolean("Smooth Return to Cursor", default = true)
        .withDescription("After Simon Says completes, turn the server aim back to your cursor before ending No Rotate.")
        .showIf { autoSolve.value && noRotate.value }
    private val returnSpeed by number("Cursor Return Speed", default = 45.0, min = 1.0, max = 90.0, step = 1.0, unit = "°/t")
        .withDescription("Maximum server-aim turn per tick while returning to your cursor.")
        .showIf { autoSolve.value && noRotate.value && smoothReturn.value }
    private val clickOnContact by boolean("Click On Contact", default = true)
        .withDescription("With No Rotate, click as soon as the server aim reaches the button instead of after the turn ends.")
        .showIf { autoSolve.value && (noRotate.value || startNoRotate.value) }
    private val contactDelay by number("Contact Delay", default = 2.0, min = 0.0, max = 5.0, step = 1.0, unit = "ticks")
        .withDescription("Wait a random number of ticks on the button, up to this many, before an on-contact click.")
        .showIf { autoSolve.value && (noRotate.value || startNoRotate.value) && clickOnContact.value }

    private fun exclusiveBoolean(name: String, other: () -> BooleanSetting) =
        boolean(name).onChange { enabled -> if (enabled) other().value = false }

    private val panicKey by keybind("Override Key")
        .withDescription("Cuts the solver dead and switches it off, for when it starts doing something stupid. Press it again to turn the same mode back on.")

    private val resetButton by button(
        name = "Reset State",
        action = { resetAll() },
        label = "Reset",
        id = "reset_state",
    ).withDescription("Forget the aim, any owed start clicks and the fact that this round was already started, so it can arm and try again without being switched off and on.")

    private val autoStart by boolean(
        "Auto Start",
        default = false
    )
        .withDescription("Click the start button automatically once Goldor speaks.").group("Auto Start")
    private val startNoRotate by boolean("No Rotate", default = false, id = "start_no_rotate")
        .withDescription("Aim using server-side rotation while your camera stays put.")
        .showIf { autoStart.value }.group("Auto Start")
    private val startClicks by number(
        "Start Clicks",
        default = 3.0,
        min = 1.0,
        max = 10.0,
        step = 1.0
    )
        .withDescription("How many times to click the start button.").showIf { autoStart.value }.group("Auto Start")
    private val startClickDelay by number(
        "Start Click Delay",
        default = 3.0,
        min = 1.0,
        max = 25.0,
        step = 1.0,
        unit = "ticks"
    )
        .withDescription("Delay between start clicks.").showIf { autoStart.value }.group("Auto Start")

    private val autoRestart by boolean("Restart On Break", default = true)
        .withDescription("Press start again when the device breaks. A failed skip is NOT a break: restarting for one costs more time than simply solving the round out.")
        .group("Auto Start")

    private val swapToLeap by boolean("Swap To Leap", default = false)
        .withDescription("Once you finish the device, swap to your Spirit Leap or Infinileap.")
    private val swapMode by mode("Swap Mode", default = SWAP, modes = listOf(SWAP, SWAP_AND_OPEN))
        .withDescription("Swap: hold the leap.\nSwap and Open: also right-click it to open the leap menu.")
        .showIf { swapToLeap.value }

    private val activationRadius by number(
        "Activation Radius",
        default = 4.0,
        min = 2.0,
        max = 10.0,
        step = 0.5,
        unit = "blocks",
    )
        .withDescription("How close to the device you must stand before it does anything. Goldor's line and the device's hologram reach the whole room, so without this it turns your view while someone else is doing the device.")
        .group("Auto Solve")
    private val mode by mode(
        "Auto Mode",
        default = "PreAim",
        modes = listOf("PreAim", "Path")
    )
        .withDescription("PreAim: hold the aim on the button to press next.\nPath: follow the pattern as it plays, then press.")
        .group("Auto Solve")
    private val autoDelay by number(
        "Auto Delay",
        default = 3.0,
        min = 1.0,
        max = 10.0,
        step = 1.0,
        unit = "ticks"
    )
        .withDescription("Minimum ticks between auto clicks.")
        .safeRange(3.0..10.0, "BANNABLE", "Under 3 ticks the clicks land closer together than a human can press")
        .group("Auto Solve")
    private val rotation = AimCurve(this, "Auto Solve")
    /** Spread within the button's size. A flat jitter pushed aims off 0.25-block plates. */
    private val aimSpread by number(
        "Aim Spread",
        default = 90.0,
        min = 0.0,
        max = 100.0,
        step = 5.0,
        unit = "%",
    )
        .withDescription("How much of the button each aim may land on, as a share of its real width, height and depth. The aim point is picked at random inside that share.")
        .safeRange(20.0..100.0, "UNSAFE", "Below 20% every aim lands on nearly the same pixel of the button, round after round")
        .group("Auto Solve")

    private val OVERRIDE_COLOR = Color(0xFFAA00)

    private const val SWAP = "Swap"
    private const val SWAP_AND_OPEN = "Swap and Open"

    /* Half the short side of the plate. */
    private const val BUTTON_RADIUS = 0.125f

    private var tick = 0

    private val aim = ButtonAim(
        owner = this,
        silent = { autoSolve.value && (noRotate.value || start.clicking && startNoRotate.value) },
        aimPoint = { SimonSaysDevice.aimPoint(it, aimSpread.value / 100.0) },
        rotationParams = { rotation.params(it, BUTTON_RADIUS) },
        now = { tick },
        contactDelay = { if (clickOnContact.value) contactDelay.value.toInt() else null },
    )
    private val start: AutoStart = AutoStart(aim, clicks = { startClicks.value.toInt() }, clickDelay = { startClickDelay.value.toInt() })
    private val leapSwap = LeapSwap(owner = this)

    /** What [panic] turned off, so the next press brings it back. */
    private var overridden: BooleanSetting? = null

    override fun init() {
        // NEVER, I REPEAT, NEVER ClientTickEvent at .Post: ac will kill you.
        register<ClientTickEvent.Pre>(EventPriority.LOW) { onTick() }
        register<ChatReceiveEvent> { onChat(event.unformatted) }
        onKeybind(panicKey, predicate = { autoSolve.value || triggerbot.value || overridden != null }) {
            val restore = overridden
            if (restore == null || autoSolve.value || triggerbot.value) panic() else resume(restore)
        }
    }

    /** Kill switch: stops mid-rotation and disables both modes. Press again to restore. */
    private fun panic() {
        overridden = if (autoSolve.value) autoSolve else triggerbot
        resetAll()
        SimonSays.reset()
        autoSolve.value = false
        triggerbot.value = false
        Toasts.show(
            I18n.trOr("blackaddons.simon_says_cheats.overridden", "Auto Simon Says: overridden, now off."),
            null,
            OVERRIDE_COLOR,
        )
    }

    private fun resume(mode: BooleanSetting) {
        overridden = null
        mode.value = true
        Toasts.show(I18n.trOr("blackaddons.simon_says_cheats.resumed", "Auto Simon Says: back on."), kind = ToastKind.SUCCESS)
    }

    private fun onTick() {
        tick++
        if (LocationUtils.f7Phase != F7Phase.GOLDOR || (!autoSolve.value && !triggerbot.value)) {
            resetAll()
            leapSwap.reset()
            return
        }
        if (swapToLeap.value) leapSwap.tick(tick, open = swapMode.value == SWAP_AND_OPEN, aimBusy = aim.busy) else leapSwap.reset()
        if (triggerbot.value) {
            resetAll()
            if (Platform.screenHost.anyScreenOpen || Platform.interaction.crosshairEntity) return
            SimonSays.solution.firstOrNull()?.let(::clickWhenAimed)
            return
        }
        if (Platform.screenHost.anyScreenOpen) {
            aim.stop()
            return
        }
        /*
         * Lost to a stronger claim or mode flipped: fresh lease. A silent start handing over to a visible solve keeps
         * the view, so the server only sees one turn.
         */
        if (aim.isStale) aim.stop(HandBack.KeepView)
        if (autoStart.value && !start.triggered && tick % DeviceHologram.POLL_TICKS == 0) start.watchDevice(::nearDevice)
        if (!nearDevice()) {
            aim.stop()
            return
        }
        start.restartOnBreak(autoRestart.value)
        if (start.drive()) return
        val target = SimonSays.solution.firstOrNull() ?: run {
            aim.endChain()
            val silent = aim.holdsSilentAim
            val devicePassed = silent && SimonSays.shownThisStage > 0 &&
                tick % DeviceHologram.POLL_TICKS == 0 && DeviceHologram.read() == DeviceHologram.ACTIVE
            if (!silent || devicePassed) resetAuto(smoothExit = devicePassed)
            return
        }

        // Decoys are never pressed, and turning toward one gives the skip away.
        if (SimonSays.mayBeDecoy) return

        when (mode.value) {
            "Path" -> runPath(target)
            else -> runPreAim(target)
        }
    }

    private fun runPreAim(target: BlockPos3) {
        aim.aimAt(SimonSaysDevice.buttonFor(target))
        clickWhenAimed(target)
    }

    /**
     * Presses only once the device listens, and moves to the next button one lantern early. During a replay
     * [SimonSays.solution] still holds the previous stage, so its size is the count before the new last lantern.
     */
    private fun runPath(target: BlockPos3) {
        val playing = SimonSays.isPlayingPattern
        val follow = SimonSays.lastLit
        val preempt = SimonSays.shownThisStage >= SimonSays.solution.size

        aim.aimAt(SimonSaysDevice.buttonFor(if (playing && !preempt && follow != null) follow else target))

        if (playing) return
        clickWhenAimed(target)
    }

    /** A configurable radius, not reach (that's a server attribute). The default starts aiming on approach. */
    private fun nearDevice(): Boolean {
        val r = activationRadius.value
        val x = Platform.player.x
        val y = Platform.player.y
        val z = Platform.player.z
        fun near(button: BlockPos3): Boolean {
            val dx = x - (button.x + 0.5)
            val dy = y - (button.y + 0.5)
            val dz = z - (button.z + 0.5)
            return dx * dx + dy * dy + dz * dz <= r * r
        }
        return near(SimonSaysDevice.START_BUTTON) || SimonSaysDevice.buttons.any(::near)
    }

    private fun clickWhenAimed(target: BlockPos3) {
        val button = SimonSaysDevice.buttonFor(target)
        if (!ButtonAim.isPresent(button)) return
        aim.clickIfAimed(button) {
            aim.forgetTarget()
            leapSwap.pressed()
            autoDelay.value.toInt()
        }
    }

    private fun onChat(message: String) {
        if (!autoSolve.value || !autoStart.value || start.triggered || LocationUtils.f7Phase != F7Phase.GOLDOR) return
        if (!SimonSaysDevice.GOLDOR_INTRO.matches(message)) return
        if (!nearDevice()) return
        start.arm()
    }

    /** Drops the aim and owed start clicks. */
    private fun resetAuto(smoothExit: Boolean = false) {
        val ease = smoothExit && smoothReturn.value && aim.holdsSilentAim
        aim.reset(if (ease) HandBack.Ease(returnSpeed.value.toFloat()) else HandBack.Discard)
        start.cancelClicks()
    }

    /** Also clears the per-round start trigger. */
    private fun resetAll() {
        resetAuto()
        start.reset()
    }
}
