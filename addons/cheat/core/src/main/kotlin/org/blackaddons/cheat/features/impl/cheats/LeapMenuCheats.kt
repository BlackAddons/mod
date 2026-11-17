package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.event.world.BlockInteractEvent
import org.blackaddons.api.event.world.EntityInteractEvent
import org.blackaddons.api.event.world.InventoryEvent
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.input.Keys
import org.blackaddons.api.player.Aim
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.HandBack
import org.blackaddons.api.skyblock.F7SubLocation
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.SkyblockItem
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.skyblock.dungeon.SpiritLeapMenu
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.cheat.aim.AimCurve
import org.blackaddons.cheat.leap.SpiritLeap
import org.blackaddons.core.features.impl.dungeons.leapmenu.LeapMenu

private val tr = I18n.scope("blackaddons.leap_menu_cheats")

internal object LeapMenuCheats : FeatureExtension(LeapMenu, group = "Cheats") {
    private val leapRotate by boolean("Leap Rotate", default = false)
        .withDescription("After a leap, look where the teammate was looking when you clicked their head. Skipped when they were out of render distance.")
    private val snap by boolean("Snap", default = true)
        .withDescription("Jump to their view instantly instead of turning.")
        .showIf { leapRotate.value }
    private val rotation = AimCurve(this, shown = { leapRotate.value && !snap.value })
    private val turnSpeed by number("Turn Speed", default = 45.0, min = 5.0, max = 180.0, step = 5.0, unit = "°/t")
        .withDescription("How fast the aim turns to their view, and back again with No Rotate.")
        .showIf { leapRotate.value && !snap.value }
    private val noRotate by boolean("No Rotate", default = false)
        .withDescription("Turn only the aim the server sees; your camera stays put until you move it.")
        .showIf { leapRotate.value }
    private val stageTargets = listOf(NO_TARGET) +
        DungeonClass.entries.filter { it != DungeonClass.EMPTY }.map { it.displayName } + listOf(BY_NAME, BY_LOCATION)
    private val fastLeap by boolean("Fast Leap", default = false)
        .withDescription("Left-click a Spirit Leap or Infinileap to leap straight to the teammate set below. Opens the menu when nobody is set.")
    private val clickDelay by number("Click Delay", default = 3.0, min = 0.0, max = 10.0, step = 1.0, unit = "ticks")
        .withDescription("How long to wait after the leap menu opens before clicking the teammate's head.")
        .showIf { fastLeap.value }
    private val doorOpener by boolean("Door Opener", default = true)
        .withDescription("During clear, leap to whoever opened the last Wither door.")
        .showIf { fastLeap.value }
    private val s1Leap by mode("S1 Leap", NO_TARGET, stageTargets)
        .withDescription("From P2 into section 1: leap to this class, a player by name, or whoever is at SS (Location).")
        .showIf { fastLeap.value }
    private val s1Name by string("S1 Name", default = "", placeholder = "Player name", maxLength = 16)
        .showIf { fastLeap.value && s1Leap.value == BY_NAME }
    private val s2Leap by mode("S2 Leap", NO_TARGET, stageTargets)
        .withDescription("From section 1 into section 2: leap to this class, a player by name, or whoever is at EE2 (Location).")
        .showIf { fastLeap.value }
    private val s2Name by string("S2 Name", default = "", placeholder = "Player name", maxLength = 16)
        .showIf { fastLeap.value && s2Leap.value == BY_NAME }
    private val s3Leap by mode("S3 Leap", NO_TARGET, stageTargets)
        .withDescription("From section 2 into section 3: leap to this class, a player by name, or whoever is at EE3 (Location).")
        .showIf { fastLeap.value }
    private val s3Name by string("S3 Name", default = "", placeholder = "Player name", maxLength = 16)
        .showIf { fastLeap.value && s3Leap.value == BY_NAME }
    private val s4Leap by mode("S4 Leap", NO_TARGET, stageTargets)
        .withDescription("From section 3 into section 4: leap to this class, a player by name, or whoever is at Core (Location).")
        .showIf { fastLeap.value }
    private val s4Name by string("S4 Name", default = "", placeholder = "Player name", maxLength = 16)
        .showIf { fastLeap.value && s4Leap.value == BY_NAME }

    /* Named by the Goldor section u leap into: P2 leaps into S1, section N into N + 1. */
    private val stageLeaps = listOf(s1Leap, s2Leap, s3Leap, s4Leap)
    private val stageNames = listOf(s1Name, s2Name, s3Name, s4Name)
    private val stageSpots = listOf(F7SubLocation.SS, F7SubLocation.EE2, F7SubLocation.EE3, F7SubLocation.CORE)

    private const val APPLY_TICKS = 20
    private const val NO_TARGET = "None"
    private const val BY_NAME = "Name"
    private const val BY_LOCATION = "Location"

    private class Target(val name: String, val yaw: Float, val pitch: Float)

    private var target: Target? = null
    private var landedTicks = -1
    private var aim: Aim? = null
    private var heldYaw = 0f
    private var heldPitch = 0f
    private val leap = SpiritLeap()

    override fun init() {
        register<MouseClickEvent> {
            if (!event.isPress || event.button != Keys.MOUSE_LEFT || !fastLeapArmed()) return@register
            event.cancelled = true
            /* A second right-click would get stuck behind the opening menu and never go out. */
            if (leap.isActive) return@register
            val stage = LocationUtils.p3Section ?: 0.takeIf { LocationUtils.f7Phase == F7Phase.STORM }
            val name = if (fastLeapSet(stage)) fastLeapTarget(stage) else null
            if (name == null) {
                if (fastLeapSet(stage)) Toasts.show(tr("nobody", "Fast Leap: can't find anyone to leap to."), kind = ToastKind.ERROR)
                Platform.interaction.submit(ClientAction.UseItem, owner = this)
                return@register
            }
            leap.start(name, clickDelay.value.toInt()) { failure -> failure?.let { Toasts.show(message(it, name), kind = ToastKind.ERROR) } }
        }
        register<BlockInteractEvent.Attack>(EventPriority.HIGHEST) { if (fastLeapArmed()) event.cancelled = true }
        register<EntityInteractEvent.Attack>(EventPriority.HIGHEST) { if (fastLeapArmed()) event.cancelled = true }
        register<InventoryEvent.SlotClick> {
            if (!leapRotate.value || !SpiritLeapMenu.isOpen) return@register
            val name = SpiritLeapMenu.heads().entries.firstOrNull { it.value == event.slotId }?.key ?: return@register
            target = Platform.world.playerRotation(name)?.let { (yaw, pitch) -> Target(name, yaw, pitch) }
            landedTicks = -1
        }
        register<ChatReceiveEvent> {
            val name = SpiritLeapMenu.leapedTo(event.unformatted) ?: return@register
            if (target?.name.equals(name, ignoreCase = true)) landedTicks = 0 else target = null
        }
        register<ClientTickEvent.Post> {
            leap.tick()
            drive()
            val t = target ?: return@register
            if (landedTicks < 0) return@register
            if (Platform.screenHost.anyScreenOpen) {
                if (++landedTicks > APPLY_TICKS) target = null
                return@register
            }
            target = null
            turn(t)
        }
        register<WorldChangeEvent> {
            leap.cancel()
            target = null
            aim = null
        }
    }

    private fun turn(t: Target) {
        releaseAim()
        val lease = Platform.rotation.request(this, silent = noRotate.value)
        if (snap.value) lease.set(t.yaw, t.pitch) else lease.turnTo(t.yaw, t.pitch, rotation.params(turnSpeed.value.toFloat()))
        aim = lease
        heldYaw = Platform.player.yaw
        heldPitch = Platform.player.pitch
    }

    /* A camera turn ends once we land, a silent one holds until the player moves the camera. */
    private fun drive() {
        val lease = aim ?: return
        when {
            lease.isEnded -> aim = null
            lease.isTurning -> Unit
            !lease.silent || Platform.player.yaw != heldYaw || Platform.player.pitch != heldPitch -> releaseAim()
        }
    }

    private fun releaseAim() {
        aim?.release(if (snap.value) HandBack.Discard else HandBack.Ease(turnSpeed.value.toFloat()))
        aim = null
    }

    /* [stage] is the section u leap into, or null while clearing (Door Opener). S4 has no leap. */
    private fun fastLeapSet(stage: Int?): Boolean =
        if (stage == null) doorOpener.value && !LocationUtils.inBoss else stageLeaps.getOrNull(stage)?.value.let { it != null && it != NO_TARGET }

    private fun fastLeapTarget(stage: Int?): String? {
        if (stage == null) return Dungeon.run.lastDoorOpener?.name
        val alive = Dungeon.team.others.filter { !it.isDead }
        return when (val choice = stageLeaps[stage].value) {
            BY_NAME -> stageNames[stage].value.trim().takeIf { it.isNotEmpty() }?.let { name ->
                /* Without a tab list (P3Sim) there's no team to check the name against. */
                if (Dungeon.team.others.isEmpty()) name else alive.firstOrNull { it.name.equalsIgnoreCase(name) }?.name
            }
            BY_LOCATION -> LeapMenu.teammateAt(stageSpots[stage])
            else -> alive.firstOrNull { it.dungeonClass == DungeonClass.fromName(choice) }?.name
        }
    }

    private fun fastLeapArmed(): Boolean =
        fastLeap.value && !Platform.screenHost.anyScreenOpen && LocationUtils.inDungeon &&
            Platform.player.inventoryItem(Platform.interaction.selectedSlot)?.let(SkyblockItem.SPIRIT_LEAP) == true

    private fun message(failure: SpiritLeap.Failure, name: String): String = when (failure) {
        SpiritLeap.Failure.NO_LEAP -> tr("no_leap", "Fast Leap: no Spirit Leap or Infinileap in your hotbar.")
        SpiritLeap.Failure.MENU_NEVER_OPENED -> tr("no_menu", "Fast Leap: the leap menu did not open.")
        SpiritLeap.Failure.NOT_IN_MENU -> tr("not_in_menu", "Fast Leap: %s is not in the leap menu.", name)
    }
}
