package org.blackaddons.cheat.features.impl.cheats.autoRelic

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.ServerTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.event.world.EntityInteractEvent
import org.blackaddons.api.event.world.EntitySnapshot
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.*
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.ServerClock
import org.blackaddons.api.world.Box3d
import org.blackaddons.api.world.Vec3d
import org.blackaddons.api.world.ViewAngles
import org.blackaddons.cheat.aim.AimCurve
import java.awt.Color
import java.util.UUID
import kotlin.math.abs

private val tr = I18n.scope("blackaddons.auto_relic")

/**
 * M7 P5 relics: walks red and orange to their cauldrons, the other three leap ([RelicLeap]). Clicks wait for the server
 * rotation, timed by a [ServerClock] so lag doesn't expire them early.
 */
object AutoRelic : Feature(
    description = "Grabs relics in M7 phase 5, then walks the red and orange ones to their cauldrons and places them.",
    name = "Auto Relic",
) {

    override val section = Categories.Cheats.Dungeons

    private const val SMOOTH = "Smooth"
    private const val SNAP = "Snap"

    private val rotation by mode("Rotation", default = SMOOTH, modes = listOf(SMOOTH, SNAP))
        .withDescription("Smooth turns in a set time, like a practiced flick. Snap faces the target within the same tick.")
        .unsafeWhen("UNSAFE", "No hand turns a hundred degrees between two ticks") { it == SNAP }

    private val aimCurve = AimCurve(this, shown = { rotation.value == SMOOTH })

    private val rotationJitter by number("Rotation Jitter", default = 0.07, min = 0.0, max = 0.3, step = 0.01, unit = "blocks")
        .withDescription("Random offset added to each smooth aim point, so repeated grabs and places do not land on the exact same spot.")
        .showIf { rotation.value == SMOOTH }

    private val noRotate by boolean("No Rotate")
        .withDescription("Turn only the direction the server sees; your camera stays put. Walking follows that hidden direction.")

    private val autoWalk by boolean("Auto Walk", default = true)
        .withDescription("Walk a red or orange relic to its cauldron. Pressing W, A, S, D, Space or Shift cancels everything.")

    private val grabAura by boolean("Grab Aura")
        .withDescription("Pick up any relic within entity reach.")

    private val placeAura by boolean("Place Aura")
        .withDescription("Place a red or orange relic as soon as its cauldron is within block reach.")

    private val earlyTrigger by boolean("Early Trigger", default = true)
        .withDescription("Start the moment a relic is clicked rather than when it lands in your hotbar. Gives up if it has not arrived 5 ticks later.")

    private val leapToCarrier by boolean("Leap To Carrier", default = true)
        .withDescription("Once green, blue or purple is in your hotbar, leap to whoever took red (for green) or orange (for blue and purple). Falls back to the Archer or Berserk while nobody has.")

    // Hypixel always uses the last hotbar slot.
    private const val RELIC_SLOT = Hotbar.SIZE - 1
    // Search is eye-centered, the stand position is its feet.
    private const val STAND_SEARCH_SLACK = 2.0
    private const val CONFIRM_TIMEOUT_TICKS = 5
    private const val RETRY_TICKS = 5
    private const val AIM_TOLERANCE_DEG = 1f
    private const val CANCEL_SOUND = "minecraft:block.note_block.bass"
    private val WALK_KEYS = setOf(MoveKey.FORWARD, MoveKey.SPRINT)
    private val ALERT_COLOR = Color(255, 85, 85)
    private val SNAP_PARAMS = RotationParams(humanize = false)

    private var carrying: Relic? = null
    private var confirmed = false
    private var arrivalDeadline: ServerClock.Deadline? = null
    // Cancelled by the player's keys, and not picked up again until it leaves the hotbar.
    private var cancelledRelic: Relic? = null
    // Keys already held at the start (W into the pedestal) don't count as a cancel.
    private var heldKeys: Set<MoveKey>? = null
    private var pending: ClientAction? = null
    private var aimPoint: Vec3d? = null
    private var aimedThisTick = false
    private var aim: Aim? = null
    private var walk: Lease? = null
    private val serverClock = ServerClock()
    private var grabRetryAt: ServerClock.Deadline? = null
    private var placeRetryAt: ServerClock.Deadline? = null
    private val relicLeap = RelicLeap(ALERT_COLOR)

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
        register<ServerTickEvent> { onServerTick() }
        register<EntityInteractEvent.Use> { onInteract(event.entity) }
        register<MovementInputEvent> { onMovementInput(event) }
        register<ChatReceiveEvent> { relicLeap.onChat(event.unformatted) }
        register<WorldChangeEvent> {
            reset()
            relicLeap.reset()
            cancelledRelic = null
        }
    }

    override fun onDisable() {
        super.onDisable()
        reset()
        relicLeap.reset()
        grabRetryAt = null
        placeRetryAt = null
        cancelledRelic = null
    }

    private fun onServerTick() = serverClock.tick()

    private fun retryReady(at: ServerClock.Deadline?): Boolean = at == null || serverClock.passed(at)

    private fun onTick() {
        aimedThisTick = false
        pending = null

        if (!inPhase()) {
            reset()
            relicLeap.reset()
            return
        }
        val held = heldRelic()
        if (held != cancelledRelic) cancelledRelic = null
        // Before the screen check, because the leap menu is a screen.
        if (leapToCarrier.value) relicLeap.tick(held) else relicLeap.stop()
        if (Platform.movement.isScreenOpen) return

        val current = carrying
        when {
            current == null -> if (held?.carriedOnFoot == true && held != cancelledRelic) start(held, confirmed = true)
            held == current -> confirmed = true
            confirmed -> {
                // Left the hotbar: placed or dropped.
                reset()
                return
            }
            arrivalDeadline?.let(serverClock::passed) == true -> {
                reset()
                Platform.chat.local(tr("no_relic", "Auto Relic: the relic never arrived, stopped."), ALERT_COLOR)
                return
            }
        }

        val relic = carrying
        if (relic != null) {
            deliver(relic)
        } else if (held == null && grabAura.value) {
            grab()
        }
        flush()
        if (!aimedThisTick) releaseAim()
    }

    // This tick's decision replaces last tick's: one request per owner.
    private fun flush() {
        val action = pending ?: return
        pending = null
        Platform.interaction.submit(action, owner = this) { result ->
            if (result == ClientActionResult.SENT) onSent(action)
        }
    }

    private fun onSent(action: ClientAction) {
        when (action) {
            is ClientAction.Interact -> {
                grabRetryAt = serverClock.after(RETRY_TICKS)
                // Our click never reaches EntityInteractEvent (use key only).
                if (earlyTrigger.value) relicOn(action.entity)?.takeIf { it.carriedOnFoot }?.let(::begin)
            }
            is ClientAction.UseOn -> placeRetryAt = serverClock.after(RETRY_TICKS)
            else -> Unit
        }
    }

    private fun onInteract(stand: EntitySnapshot) {
        if (!earlyTrigger.value || carrying != null || !inPhase() || heldRelic() != null) return
        relicOn(stand.uuid)?.takeIf { it.carriedOnFoot }?.let(::begin)
    }

    // Starts before the relic reaches the hotbar, so the turn makes it into this tick's movement packet.
    private fun begin(relic: Relic) {
        start(relic, confirmed = false)
        deliver(relic)
    }

    private fun start(relic: Relic, confirmed: Boolean) {
        carrying = relic
        this.confirmed = confirmed
        arrivalDeadline = if (confirmed) null else serverClock.after(CONFIRM_TIMEOUT_TICKS)
        heldKeys = null
        if (walk?.isActive != true) walk = Platform.movement.drive(this) { walkKeys() }
    }

    private fun deliver(relic: Relic) {
        val cauldron = relic.cauldron
        aim(Platform.world.blockAimPoint(cauldron.x, cauldron.y, cauldron.z) ?: cauldron.center())
        if (!placeAura.value || !confirmed || !retryReady(placeRetryAt) || !inBlockReach(relic)) return
        if (Platform.interaction.selectedSlot != RELIC_SLOT) Platform.interaction.selectHotbarSlot(RELIC_SLOT)
        pending = ClientAction.UseOn(cauldron)
    }

    private fun grab() {
        if (!retryReady(grabRetryAt)) return
        val eye = Platform.player.eyePos
        val reach = Platform.player.entityReach
        val (stand, box) = Platform.world.entitiesNear(eye.x, eye.y, eye.z, reach + STAND_SEARCH_SLACK)
            .asSequence()
            .filter { it.isArmorStand }
            .mapNotNull { stand -> Platform.world.entityBox(stand.uuid)?.let { stand to it } }
            .filter { (_, box) -> box.distanceSqr(eye) <= reach * reach }
            .filter { (stand, _) -> relicOn(stand.uuid) != null }
            .minByOrNull { (_, box) -> box.distanceSqr(eye) }
            ?: return
        aim(box.center())
        pending = ClientAction.Interact(stand.uuid)
    }

    private fun aim(point: Vec3d) {
        aimedThisTick = true
        // Lost to a stronger claim or mode changed: fresh lease.
        if (aim?.let { it.isEnded || it.silent != noRotate.value } == true) releaseAim()
        val lease = aim ?: Platform.rotation.request(this, silent = noRotate.value).also { aim = it }

        val (yaw, pitch) = ViewAngles.between(Platform.player.eyePos, point)
        val off = maxOf(
            abs(ViewAngles.yawDelta(Platform.movement.serverYaw, yaw)),
            abs(pitch - Platform.movement.serverPitch),
        )
        if (lease.isActive && off <= maxOf(AIM_TOLERANCE_DEG, Platform.rotation.turnStep)) return

        if (rotation.value == SNAP) {
            lease.turnToPoint(point, SNAP_PARAMS)
            lease.snap()
        } else if (point != aimPoint) {
            lease.turnToPoint(point, aimCurve.params(randomness = rotationJitter.value.toFloat()))
        }
        aimPoint = point
    }

    private fun releaseAim() {
        aim?.release()
        aim = null
        aimPoint = null
    }

    // The player's own keys. Pressing one after the start cancels.
    private fun onMovementInput(input: MovementInputEvent) {
        if (carrying == null || !autoWalk.value) return
        val pressed = input.pressed - MoveKey.SPRINT
        val before = heldKeys ?: pressed
        heldKeys = before intersect pressed
        if ((pressed - before).isNotEmpty()) cancel()
    }

    // Returning null hands the keys back: without Auto Walk the player walks, and in reach there's nowhere to go.
    private fun walkKeys(): Set<MoveKey>? {
        val relic = carrying ?: return null
        if (!autoWalk.value || Platform.movement.isScreenOpen || inBlockReach(relic)) return null
        return WALK_KEYS
    }

    private fun cancel() {
        val relic = carrying
        reset()
        // Otherwise next tick restarts and walks against the player's keys.
        cancelledRelic = relic
        Platform.chat.local(tr("cancelled", "Auto Relic: cancelled by your input."), ALERT_COLOR)
        Platform.feedback.showTitle("§c" + tr("cancelled_title", "Relic cancelled"), stay = 20)
        Platform.feedback.playSound(CANCEL_SOUND, pitch = 0.5f)
    }

    private fun reset() {
        walk?.release()
        walk = null
        carrying = null
        confirmed = false
        arrivalDeadline = null
        heldKeys = null
        pending = null
        Platform.interaction.cancel(this)
        releaseAim()
    }

    private fun inPhase(): Boolean =
        Platform.player.isInGame && Platform.player.health > 0f && LocationUtils.isMasterMode && LocationUtils.f7Phase == F7Phase.WITHER_KING

    private fun heldRelic(): Relic? = Platform.player.inventoryItem(RELIC_SLOT)?.let { Relic.fromItemName(it.name) }

    private fun relicOn(stand: UUID): Relic? = Platform.world.headItem(stand)?.let { Relic.fromItemName(it.name) }

    private fun inBlockReach(relic: Relic): Boolean {
        val reach = Platform.player.blockReach
        return Box3d.of(relic.cauldron).distanceSqr(Platform.player.eyePos) <= reach * reach
    }
}
