package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.input.KeyToggle
import org.blackaddons.api.player.Lease
import org.blackaddons.api.player.MouseButton
import org.blackaddons.api.player.RaycastHit
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.equalsIgnoreCase
import java.util.concurrent.ThreadLocalRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

object AutoClicker : Feature(
    description = "Automatically clicks left/right mouse buttons with customizable speeds and filters.",
    name = "Auto Clicker",
) {
    private val clickButton by mode("Button", default = "Left", modes = listOf("Left", "Right", "Both"))
    private val minCps by number("Min CPS", default = 5.0, min = 1.0, max = 20.0, step = 0.5)
    private val maxCps by number("Max CPS", default = 8.0, min = 1.0, max = 20.0, step = 0.5)
    private val holdMode by boolean("Hold Keybind", default = false)
        .withDescription("Active only while the hotkey is held down. If disabled, the hotkey toggles on/off.")
    private val holdClick by boolean("Hold Click", default = false)
        .withDescription("Hold down the click button continuously instead of rapid tapping.")
    private val groundOnly by boolean("Ground Only", default = false)
        .withDescription("Only click while the player is on the ground.")
    private val toggleKey by keybind("Hotkey")
        .withDescription("Press/hold to activate the clicker.")

    private val lookAtFilterEnabled by boolean("Look At Filter", default = false).group("Look At Filter")
    private val lookAtTargets by list("Look At Targets", default = emptyList(), placeholder = "minecraft:zombie").group("Look At Filter")
    private val lookAtFilterDistance by number("Look At Distance", default = 3.5, min = 1.0, max = 100.0, step = 0.1).group("Look At Filter")

    private val itemFilterEnabled by boolean("Item Filter", default = false).group("Item Filter")
    private val itemFilters by list("Item Name Filters", default = emptyList(), placeholder = "Claymore").group("Item Filter")

    private val locationConditionsEnabled by boolean("Location Conditions", default = false).group("Location Conditions")
    private val inDungeonFilter by boolean("Dungeon Only", default = false).group("Location Conditions").showIf { locationConditionsEnabled.value }
    private val floorFilter by mode("Floor Filter", default = "Any", modes = listOf("Any") + DungeonFloor.entries.map { it.name })
        .group("Location Conditions").showIf { locationConditionsEnabled.value }
    private val inBossFilter by boolean("Boss Room Only", default = false).group("Location Conditions").showIf { locationConditionsEnabled.value }
    private val phaseFilter by mode("Phase Filter", default = "Any", modes = listOf("Any", "1", "2", "3", "4", "5"))
        .group("Location Conditions")
        .showIf { DungeonFloor.entries.any { it.isF7orM7 && it.name == floorFilter.value } && locationConditionsEnabled.value }

    private val activation = KeyToggle { holdMode.value }
    private val active: Boolean get() = activation.active
    private var holds: List<Lease> = emptyList()
    private var heldButtons: List<MouseButton> = emptyList()
    private val clock = TimeSource.Monotonic.markNow()
    private var nextClickDue: Duration? = null

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
    }

    override fun onDisable() {
        super.onDisable()
        deactivate()
    }

    private fun onTick() {
        if (!Platform.player.isInGame || Platform.screenHost.anyScreenOpen) {
            deactivate()
            return
        }

        updateActiveState()

        if (!active || !passesGroundCheck() || !passesItemFilter() || !passesLookAtFilter() || !passesLocationFilter()) {
            releaseButtons()
            return
        }

        if (holdClick.value) {
            holdButtons()
        } else {
            releaseButtons()
            val now = clock.elapsedNow()
            val due = nextClickDue
            if (due == null || now >= due) {
                fireClicks()
                nextClickDue = nextDue(due, now, randomInterval())
            }
        }
    }

    private fun deactivate() {
        releaseButtons()
        activation.active = false
        activation.release()
        nextClickDue = null
    }

    private fun holdButtons() {
        val wanted = when (clickButton.value) {
            "Left" -> listOf(MouseButton.ATTACK)
            "Right" -> listOf(MouseButton.USE)
            else -> listOf(MouseButton.ATTACK, MouseButton.USE)
        }
        if (heldButtons == wanted && holds.all { it.isActive }) return
        releaseButtons()
        holds = wanted.map { Platform.interaction.hold(it, this) }
        heldButtons = wanted
    }

    private fun releaseButtons() {
        holds.forEach(Lease::release)
        holds = emptyList()
        heldButtons = emptyList()
    }

    private fun updateActiveState() {
        if (!toggleKey.isBound) {
            activation.active = true
            return
        }
        val was = active
        if (!activation.update(toggleKey.isPressed) && was && !holdMode.value) nextClickDue = null
    }

    private fun passesGroundCheck(): Boolean {
        if (!groundOnly.value) return true
        return Platform.player.onGround
    }

    private fun passesItemFilter(): Boolean {
        if (!itemFilterEnabled.value) return true
        val filters = itemFilters.value
        if (filters.isEmpty()) return true
        val heldName = Platform.player.mainHandItem?.name ?: return false
        return filters.any { heldName.containsIgnoreCase(it) }
    }

    private fun passesLookAtFilter(): Boolean {
        if (!lookAtFilterEnabled.value) return true
        val targets = lookAtTargets.value
        if (targets.isEmpty()) return true

        val id = when (val hit = Platform.raycast.lookingAt(lookAtFilterDistance.value)) {
            is RaycastHit.Entity -> hit.entity.typeId
            is RaycastHit.Block -> hit.blockId
            null -> return false
        }
        return targets.any { it.trim().equalsIgnoreCase(id) }
    }

    private fun passesLocationFilter(): Boolean {
        if (!locationConditionsEnabled.value) return true
        if (inDungeonFilter.value && !LocationUtils.inDungeon) {
            return false
        }
        if (floorFilter.value != "Any") {
            if (LocationUtils.dungeonFloor?.name != floorFilter.value) {
                return false
            }
        }
        if (inBossFilter.value && !LocationUtils.inBoss) {
            return false
        }
        if (phaseFilter.value != "Any") {
            val phase = LocationUtils.f7Phase
            if (phase == null || phase.number.toString() != phaseFilter.value) {
                return false
            }
        }
        return true
    }

    private fun fireClicks() {
        val interaction = Platform.interaction
        when (clickButton.value) {
            "Left" -> interaction.leftClick()
            "Right" -> interaction.rightClick()
            else -> {
                interaction.leftClick()
                interaction.rightClick()
            }
        }
    }

    private fun randomInterval(): Duration {
        val min = minCps.value
        val max = maxCps.value
        val minVal = min.coerceIn(1.0, max)
        val maxVal = max.coerceIn(minVal, 20.0)
        val cps = ThreadLocalRandom.current().nextDouble(minVal, maxVal + 0.001)
        return 1.seconds / cps
    }

    internal fun nextDue(due: Duration?, now: Duration, interval: Duration): Duration {
        val next = (due ?: return now + interval) + interval
        return if (now - next >= interval) now + interval else next
    }
}
