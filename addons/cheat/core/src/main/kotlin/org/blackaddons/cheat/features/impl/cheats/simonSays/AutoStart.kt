package org.blackaddons.cheat.features.impl.cheats.simonSays

import org.blackaddons.core.features.impl.dungeons.simonSays.SimonSays
import org.blackaddons.core.features.impl.dungeons.simonSays.SimonSaysDevice

/* Presses AutoSS's start button once per round (Goldor's line or the hologram), and again after a break. */
internal class AutoStart(
    private val aim: ButtonAim,
    private val clicks: () -> Int,
    private val clickDelay: () -> Int,
) {
    private var clicksLeft = 0

    val clicking: Boolean get() = clicksLeft > 0

    /** Armed once per phase by whichever signal shows up first. */
    var triggered = false
        private set
    private var lastHologram = DeviceHologram.NONE
    private var restartSpent = false

    fun arm() {
        triggered = true
        clicksLeft = clicks()
    }

    /**
     * Hologram trigger, since mods can hide Goldor's line (NoammAddons does). Edges are tracked anywhere but only arm
     * at the device: a round that started without u is someone else's.
     */
    fun watchDevice(nearDevice: () -> Boolean) {
        val seen = DeviceHologram.read()
        if (lastHologram == DeviceHologram.NONE && seen == DeviceHologram.INACTIVE && nearDevice()) arm()
        lastHologram = seen
    }

    /* One restart per break. A dead device won't listen to more anyway. */
    fun restartOnBreak(enabled: Boolean) {
        if (!SimonSays.isBroken) {
            restartSpent = false
        } else if (enabled && !restartSpent) {
            restartSpent = true
            clicksLeft = clicks()
        }
    }

    /** Aims at start and clicks it. True while clicks are owed, which holds off the solve loop. */
    fun drive(): Boolean {
        if (clicksLeft <= 0) return false
        if (SimonSays.solution.isNotEmpty()) {
            clicksLeft = 0
            return false
        }
        val start = SimonSaysDevice.START_BUTTON
        aim.aimAt(start)
        aim.clickIfAimed(start) {
            aim.forgiveMisses()
            clicksLeft--
            clickDelay()
        }
        return true
    }

    fun cancelClicks() {
        clicksLeft = 0
    }

    /* So we hear the next trigger again. */
    fun reset() {
        triggered = false
        lastHologram = DeviceHologram.NONE
        restartSpent = false
    }
}
