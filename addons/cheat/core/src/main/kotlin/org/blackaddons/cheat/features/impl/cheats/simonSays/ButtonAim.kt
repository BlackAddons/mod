package org.blackaddons.cheat.features.impl.cheats.simonSays

import org.blackaddons.api.Platform
import org.blackaddons.api.player.Aim
import org.blackaddons.api.player.ClickRequest
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.ClientActionResult
import org.blackaddons.api.player.HandBack
import org.blackaddons.api.player.ReactionMode
import org.blackaddons.api.player.RotationParams
import org.blackaddons.api.world.BlockHitboxes
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.cheat.aim.ContactClick
import org.blackaddons.cheat.features.impl.cheats.simonSays.ButtonAim.Companion.MAX_MISSED_AIMS
import org.blackaddons.core.features.impl.dungeons.simonSays.SimonSaysDevice

/*
 * AutoSS button aiming and clicking. [owner] holds the lease and clicks, so the hosts drop both on disable. [silent]
 * says whether the aim is No Rotate. [contactDelay] is the most ticks a silent aim can wait on the button before it
 * clicks mid-turn. Null waits for the turn to end.
 */
internal class ButtonAim(
    private val owner: Any,
    private val silent: () -> Boolean,
    private val aimPoint: (BlockPos3) -> Vec3d,
    private val rotationParams: (ReactionMode) -> RotationParams,
    private val now: () -> Int,
    private val contactDelay: () -> Int? = { null },
) {
    private var aim: Aim? = null

    private var contact: ContactClick? = null

    /* A released silent aim that's still easing back to the camera. */
    private var handingBack: Aim? = null

    private var currentAim: BlockPos3? = null

    /** No second click until the host settles this one. */
    private var pendingClick: ClickRequest? = null
    private var nextClickTick = 0

    /* Nothing pressable since the last aim: the next button starts a new chain. */
    private var newChain = true

    private var missedAims = 0

    val busy: Boolean get() = aim != null || handingBack?.isTurning == true

    val holdsSilentAim: Boolean get() = aim?.silent == true

    /* Lost to a stronger claim, or No Rotate got toggled. */
    val isStale: Boolean get() = aim?.let { it.isEnded || it.silent != silent() } == true

    /**
     * Aims at [button], re-aiming if the turn missed: the thin plate plus jitter can land on the obsidian behind it.
     * Damped: only after a turn finishes, and at most [MAX_MISSED_AIMS] fresh jitters.
     */
    fun aimAt(button: BlockPos3) {
        if (currentAim == button && !turning()) {
            if (silent()) return
            if (BlockHitboxes.realCrosshairBlock() == button) {
                missedAims = 0
                return
            }
            if (!isPresent(button)) return
        }
        reaim(button)
    }

    /**
     * Clicks once aimed, idle and past the delay. [onSent] returns ticks to the next click. A blocked click re-aims. No
     * visible click while an entity is under the crosshair, it would hit the entity.
     */
    fun clickIfAimed(button: BlockPos3, onSent: () -> Int) {
        if (!silent() && Platform.interaction.crosshairEntity) return
        if (!aimedAt(button)) return
        if (now() < nextClickTick || pendingClick?.isPending == true) return
        val action = if (silent()) ClientAction.UseOn(button, onContact = contact != null) else ClientAction.Use
        pendingClick = Platform.interaction.submit(action, owner = owner) { result ->
            when (result) {
                ClientActionResult.SENT -> nextClickTick = now() + onSent()
                ClientActionResult.BLOCKED -> reaim(button)
                else -> Unit
            }
        }
    }

    /* The pressed button might be next too, so it needs a fresh aim. */
    fun forgetTarget() {
        currentAim = null
    }

    fun forgiveMisses() {
        missedAims = 0
    }

    fun endChain() {
        newChain = true
    }

    fun stop(handBack: HandBack = HandBack.Discard) {
        aim?.release(handBack)
        if (handBack is HandBack.Ease) handingBack = aim
        aim = null
        currentAim = null
    }

    /* Drops the aim and the click in flight. */
    fun reset(handBack: HandBack = HandBack.Discard) {
        pendingClick?.cancel()
        pendingClick = null
        stop(handBack)
        missedAims = 0
    }

    private fun reaim(target: BlockPos3) {
        if (currentAim != target) {
            missedAims = 0
            issueAim(target)
            return
        }
        if (turning()) return
        if (missedAims >= MAX_MISSED_AIMS) return
        missedAims++
        issueAim(target)
    }

    private fun issueAim(button: BlockPos3) {
        val lease = aim ?: Platform.rotation.request(owner, silent = silent()).also { aim = it }
        val chainStart = newChain && button != SimonSaysDevice.START_BUTTON
        if (chainStart) newChain = false
        val reaction = if (chainStart) ReactionMode.NORMAL else ReactionMode.EXPECTED
        lease.turnToPoint(aimPoint(button), rotationParams(reaction))
        currentAim = button
        contact = contactDelay()?.let(::ContactClick)
    }

    private fun turning(): Boolean = aim?.isTurning == true

    private fun aimedAt(button: BlockPos3): Boolean {
        if (!silent()) return BlockHitboxes.realCrosshairBlock() == button
        if (currentAim != button || aim?.isActive != true) return false
        return !turning() || contact?.ready(Platform.interaction.sentTargetBlock == button) == true
    }

    companion object {
        /** Fresh jitters before we give up on this aim. */
        private const val MAX_MISSED_AIMS = 3

        fun isPresent(button: BlockPos3): Boolean =
            Platform.world.blockIdAt(button.x, button.y, button.z) == SimonSaysDevice.STONE_BUTTON
    }
}
