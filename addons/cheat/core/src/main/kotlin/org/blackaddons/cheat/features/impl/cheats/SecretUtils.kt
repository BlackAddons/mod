package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.world.BlockInteractEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.*
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonUtils
import org.blackaddons.api.skyblock.dungeon.SecretChests
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.world.BlockHitboxOverride
import org.blackaddons.api.world.BlockHitboxes
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.cheat.aim.AimCurve
import org.blackaddons.cheat.aim.ContactClick
import org.blackaddons.cheat.features.impl.cheats.SecretUtils.aimSpread
import org.blackaddons.cheat.features.impl.cheats.SecretUtils.clickOnContact
import org.blackaddons.cheat.features.impl.cheats.SecretUtils.contactDelay
import org.blackaddons.cheat.features.impl.cheats.SecretUtils.realClick
import org.blackaddons.cheat.features.impl.cheats.SecretUtils.triggerbot
import org.blackaddons.cheat.features.impl.cheats.lightsDevice.LightsDevice

/**
 * Bigger outlines on buttons, levers, secret skulls and mushrooms, only the outline grows. With [realClick], a click
 * that only the big outline caught gets cancelled and resent after turning onto the real outline, so the server sees a
 * plausible rotation. Camera aims resend on the same tick, silent aims wait for the server rotation. With
 * [clickOnContact] both click mid-turn, [contactDelay] ticks after the crosshair (or for silent aims the server's
 * rotation) reaches the real outline. [triggerbot] clicks each secret under the crosshair once per world, through the
 * same correction when only the big outline is aimed, and stops held attack from breaking secrets.
 */
object SecretUtils : Feature(
    description = "Enlarges the hitboxes of buttons, levers, secret skulls and mushrooms, and can click secrets for you.",
    name = "Secret Utils",
), BlockHitboxOverride {

    override val id = "SecretHitboxes"

    override val section = Categories.Cheats.Dungeons

    private val levers by boolean("Levers", default = true)
        .withDescription("Enlarge lever hitboxes. The Floor 7 lights device levers keep theirs.")
    private val leverSizeSetting by number("Lever Size", default = 1.0, min = 0.5, max = 1.0, step = 0.01)
        .withDescription("Side of the lever hitbox, in blocks.")
        .showIf { levers.value }
    private val buttons by boolean("Buttons", default = true)
        .withDescription("Enlarge button hitboxes.")
    private val buttonSizeSetting by number("Button Size", default = 1.0, min = 0.375, max = 1.0, step = 0.01)
        .withDescription("Side of the button hitbox, in blocks.")
        .showIf { buttons.value }
    private val skulls by boolean("Secret Skulls", default = true)
        .withDescription("Give wither essence and redstone key skulls a full-block hitbox.")
    private val mushrooms by boolean("Mushrooms", default = true)
        .withDescription("Give mushrooms a full-block hitbox.")
    private val realClick by boolean("Click Real Hitbox", default = false)
        .withDescription("When a click lands only on the enlarged part, cancel it, turn onto the real hitbox and click that instead. A real hitbox that cannot be hit is not clicked at all.")
    private val snap by boolean("Snap", default = false)
        .withDescription("Jump onto the real hitbox and click within a tick instead of turning.")
        .showIf { realClick.value }
    private val rotation = AimCurve(this, shown = { realClick.value && !snap.value })
    private val correctionSpeed by number("Correction Speed", default = 45.0, min = 5.0, max = 180.0, step = 5.0, unit = "°/t")
        .withDescription("How fast the aim turns onto the real hitbox, and back again with No Rotate.")
        .showIf { realClick.value && !snap.value }
    private val noRotate by boolean("No Rotate", default = false)
        .withDescription("Turn only the aim the server sees; your camera stays put.")
        .showIf { realClick.value }
    private val clickOnContact by boolean("Click On Contact", default = true)
        .withDescription("Click as soon as the crosshair reaches the real hitbox; the turn still finishes after the click.")
        .showIf { realClick.value && !snap.value }
    private val contactDelay by number("Contact Delay", default = 2.0, min = 0.0, max = 5.0, step = 1.0, unit = "ticks")
        .withDescription("Wait a random number of ticks on the real hitbox, up to this many, before an on-contact click.")
        .showIf { realClick.value && !snap.value && clickOnContact.value }
    private val aimSpread by number("Aim Spread", default = 90.0, min = 0.0, max = 100.0, step = 5.0, unit = "%")
        .withDescription("How much of the real hitbox the corrected aim may land on, as a share of its width, height and depth. The aim point is picked at random inside that share.")
        .safeRange(20.0..100.0, "UNSAFE", "Below 20% every corrected aim lands on nearly the same pixel of the hitbox")
        .showIf { realClick.value }
    private val triggerbot by boolean("Triggerbot", default = false)
        .withDescription("In dungeon rooms, click chests, wither essence and redstone key skulls, and the room's secret levers once each when your crosshair lands on them, and stop breaking them while attack is held so the clicks go through. Uses Click Real Hitbox when that is on.")
    private val triggerDelay by number("Trigger Delay", default = 2.0, min = 0.0, max = 5.0, step = 1.0, unit = "ticks")
        .withDescription("Wait a random number of ticks on a secret, up to this many, before the triggerbot clicks it.")
        .showIf { triggerbot.value }

    /** Unfinished corrections get dropped instead of holding the aim. */
    private const val TIMEOUT_TICKS = 40

    /** Cooldown after a failed correction so spam-clicking doesn't rescan. */
    private const val RETRY_TICKS = 5

    private const val TOAST_TICKS = 20

    private const val LEVER = "minecraft:lever"
    private val HEADS = setOf("minecraft:player_head", "minecraft:player_wall_head")

    private class Correction(val block: BlockPos3, val point: Vec3d, val radius: Float, val aim: Aim, val startTick: Int) {
        var click: ClickRequest? = null
        var clicked = false
        val contact = ContactClick(contactDelay.value.toInt())
    }

    private enum class Correct { NOT_NEEDED, NO_AIM, UNREACHABLE, STARTED }

    private var correction: Correction? = null
    private var tick = 0

    private var sentBlock: BlockPos3? = null
    private var sentTick = -1

    private var failedBlock: BlockPos3? = null
    private var failedTick = -RETRY_TICKS
    private var toastTick = -TOAST_TICKS

    /** Blocks clicked in this world. A second click would flip a lever back or reopen a chest. */
    private val clicked = HashSet<BlockPos3>()
    private var triggerBlock: BlockPos3? = null
    private var triggerContact: ContactClick? = null
    private var triggerClick: ClickRequest? = null

    /** [leverSize] runs inside `getShape`, way too hot to parse the scoreboard. */
    @Volatile
    private var inFloor7 = false

    override val buttonSize: Double? get() = if (buttons.value) buttonSizeSetting.value else null
    override val fullSecretSkulls: Boolean get() = skulls.value
    override val fullMushrooms: Boolean get() = mushrooms.value

    override fun leverSize(x: Int, y: Int, z: Int): Double? {
        if (!levers.value) return null
        // Enlarged wrong levers in the lights device would steal clicks from their neighbors.
        if (inFloor7 && BlockPos3(x, y, z) in LightsDevice.wrong) return null
        return leverSizeSetting.value
    }

    override fun init() {
        register<ClientTickEvent.Post> {
            inFloor7 = LocationUtils.hasF7Boss
        }
        register<ClientTickEvent.Pre> {
            tick++
            correction?.let(::drive)
            if (triggerbot.value) trigger() else resetTrigger()
        }
        register<BlockInteractEvent.Use> { onUse(event) }
        register<BlockInteractEvent.Attack> { if (breaksSecret(event)) event.cancelled = true }
        register<BlockInteractEvent.Mine> { if (breaksSecret(event)) event.cancelled = true }
        register<WorldChangeEvent> {
            clicked.clear()
            resetTrigger()
        }
    }

    override fun onEnable() {
        super.onEnable()
        BlockHitboxes.active = this
    }

    override fun onDisable() {
        super.onDisable()
        finish()
        resetTrigger()
        if (BlockHitboxes.active === this) BlockHitboxes.active = null
    }

    /**
     * The player's own clicks on a block being corrected get dropped, also in the tick its click is sent: two
     * placements in one tick flag MultiPlace.
     */
    private fun onUse(event: BlockInteractEvent.Use) {
        if (!realClick.value || event.synthetic) return
        val block = BlockPos3(event.x, event.y, event.z)
        if (correction?.block == block || (sentBlock == block && sentTick == tick) ||
            (failedBlock == block && tick - failedTick < RETRY_TICKS)
        ) {
            event.cancelled = true
            return
        }
        if (correction != null) return
        when (correct(block)) {
            Correct.NOT_NEEDED, Correct.NO_AIM -> Unit
            Correct.UNREACHABLE -> {
                event.cancelled = true
                fail(block, "unreachable", "Real hitbox out of reach")
            }
            Correct.STARTED -> event.cancelled = true
        }
    }

    /** Starts turning onto [block]'s real outline when the crosshair only caught the enlarged one. */
    private fun correct(block: BlockPos3): Correct {
        val enlarged = Platform.world.blockOutlineBoxes(block.x, block.y, block.z)
        val reach = Platform.player.blockReach
        val target = BlockHitboxes.vanilla {
            if (Platform.world.blockOutlineBoxes(block.x, block.y, block.z) == enlarged) return Correct.NOT_NEEDED
            if (BlockHitboxes.realCrosshairBlock() == block) return Correct.NOT_NEEDED
            reachablePoint(block, reach)
        } ?: return Correct.UNREACHABLE
        val aim = Platform.rotation.request(this, silent = noRotate.value)
        if (!aim.isActive) {
            aim.release()
            return Correct.NO_AIM
        }
        val (point, radius) = target
        turn(aim, point, radius)
        correction = Correction(block, point, radius, aim, tick)
        BlockHitboxes.active = null
        return Correct.STARTED
    }

    /**
     * Random point on the visible middle [aimSpread] of the real outline (so half-hidden buttons get hit where they
     * show), with its aim radius.
     */
    private fun reachablePoint(block: BlockPos3, reach: Double): Pair<Vec3d, Float>? {
        val box = Platform.world.blockOutlineBoxes(block.x, block.y, block.z).firstOrNull() ?: return null
        val point = box.visiblePoint(Platform.player.eyePos, aimSpread.value / 100.0) {
            Platform.raycast.blockToward(it, reach)?.pos == block
        } ?: return null
        val sides = doubleArrayOf(box.max.x - box.min.x, box.max.y - box.min.y, box.max.z - box.min.z).sorted()
        return point to (sides[1] / 2).toFloat()
    }

    private fun drive(c: Correction) {
        if (c.aim.isEnded || tick - c.startTick > TIMEOUT_TICKS) {
            finish()
            return
        }
        if (c.clicked) {
            if (!c.aim.isTurning) finish()
            return
        }
        if (c.click != null) return
        val onTarget = !c.aim.silent && BlockHitboxes.realCrosshairBlock() == c.block
        val inContact = if (c.aim.silent) Platform.interaction.sentTargetBlock == c.block else onTarget
        if (c.aim.isTurning && !(clickOnContact.value && c.contact.ready(inContact))) return
        if (!c.aim.silent && !onTarget) {
            turn(c.aim, c.point, c.radius)
            return
        }
        val action = if (c.aim.silent) ClientAction.UseOn(c.block, onContact = clickOnContact.value) else ClientAction.Use
        c.click = Platform.interaction.submit(action, owner = this) { result ->
            when (result) {
                ClientActionResult.SENT -> {
                    sent(c.block)
                    // Stopping dead on the click looks unnatural, so the turn runs out and drive() releases it.
                    if (c.aim.isTurning) {
                        c.clicked = true
                        return@submit
                    }
                }
                ClientActionResult.BLOCKED -> fail(c.block, "blocked", "Something got in the way of the click")
                else -> Unit
            }
            finish()
        }
    }

    private fun trigger() {
        if (correction != null) return
        val block = aimedSecret()
        if (block != triggerBlock) {
            resetTrigger()
            triggerBlock = block
            triggerContact = block?.let { ContactClick(triggerDelay.value.toInt()) }
        }
        if (block == null || triggerClick?.isPending == true || triggerContact?.ready(true) != true) return
        if (realClick.value) {
            when (correct(block)) {
                Correct.NOT_NEEDED -> Unit
                Correct.UNREACHABLE -> {
                    failedBlock = block
                    failedTick = tick
                    return
                }
                Correct.NO_AIM, Correct.STARTED -> return
            }
        }
        triggerClick = Platform.interaction.submit(ClientAction.Use, owner = this) { result ->
            if (result == ClientActionResult.SENT) sent(block)
        }
    }

    // While a silent aim is active, the click target isn't the crosshair.
    private fun aimedSecret(): BlockPos3? {
        if (!LocationUtils.inDungeonRooms || Platform.screenHost.anyScreenOpen) return null
        val block = Platform.interaction.crosshairBlock ?: return null
        if (Platform.interaction.clickTargetBlock != block || block in clicked) return null
        if (failedBlock == block && tick - failedTick < RETRY_TICKS) return null
        return block.takeIf(::isSecret)
    }

    /** A break in progress blocks the triggerbot's clicks, so held attack leaves secrets alone. */
    private fun breaksSecret(event: BlockInteractEvent): Boolean =
        triggerbot.value && LocationUtils.inDungeonRooms && isSecret(BlockPos3(event.x, event.y, event.z))

    private fun isSecret(block: BlockPos3): Boolean {
        val id = Platform.world.blockIdAt(block.x, block.y, block.z)
        return when {
            SecretChests.isChestBlock(id) -> true
            id == LEVER -> Dungeon.layout.isSecretLever(block)
            id in HEADS -> DungeonUtils.isSecretSkull(Platform.world.skullOwnerId(block.x, block.y, block.z))
            else -> false
        }
    }

    private fun sent(block: BlockPos3) {
        sentBlock = block
        sentTick = tick
        clicked += block
    }

    private fun resetTrigger() {
        triggerClick?.cancel()
        triggerClick = null
        triggerBlock = null
        triggerContact = null
    }

    /** Re-turns to the same point from the new position after a missed camera aim. */
    private fun turn(aim: Aim, point: Vec3d, radius: Float) {
        aim.turnToPoint(point, rotation.params(targetRadius = radius))
        if (snap.value) aim.snap()
    }

    private fun finish() {
        val c = correction ?: return
        correction = null
        c.click?.cancel()
        c.aim.release(if (snap.value) HandBack.Discard else HandBack.Ease(correctionSpeed.value.toFloat()))
        if (enabled) BlockHitboxes.active = this
    }

    private fun fail(block: BlockPos3, key: String, fallback: String) {
        failedBlock = block
        failedTick = tick
        if (tick - toastTick < TOAST_TICKS) return
        toastTick = tick
        Toasts.show(I18n.trOr("blackaddons.secret_hitboxes.$key", fallback), kind = ToastKind.ERROR)
    }
}
