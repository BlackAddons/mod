package org.blackaddons.cheat.features.impl.dev

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.DevOnly
import org.blackaddons.api.player.Aim
import org.blackaddons.api.player.ClickRequest
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.ClientActionResult
import org.blackaddons.api.player.RotationParams
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Box3d

/** Loops silent aim and button clicks on the two pictured test targets. */
@DevOnly
object NoRotateButtonTest : Feature(
    description = "Alternates silent clicks on one of two stone-button pairs.",
    name = "No Rotate Button Test",
) {
    private val useAcTestButtons by boolean("Use AC Test Buttons", default = false)
        .withDescription("Use the buttons at (-4, 84, -88) and (-1, 84, -86) instead of the Simon Says test pair.")

    private val simonButtons = listOf(BlockPos3(-2, 85, -93), BlockPos3(-2, 85, -90))
    private val acTestButtons = listOf(BlockPos3(-4, 84, -88), BlockPos3(-1, 84, -86))
    private val rotationParams = RotationParams(speed = 8f, randomness = 0f)
    private const val CLICK_DELAY_TICKS = 10

    private var tick = 0
    private var nextClickTick = 0
    private var buttonIndex = 0
    private var aimedButton: BlockPos3? = null
    private var aim: Aim? = null
    private var pendingClick: ClickRequest? = null

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
        register<WorldChangeEvent> { reset() }
    }

    override fun onDisable() = reset()

    private fun onTick() {
        tick++
        val buttons = if (useAcTestButtons.value) acTestButtons else simonButtons
        if (!Platform.player.isInGame || !Platform.world.isLoaded || Platform.screenHost.anyScreenOpen ||
            buttons.any { Platform.world.blockIdAt(it.x, it.y, it.z) != "minecraft:stone_button" }
        ) {
            releaseAim()
            return
        }

        val button = buttons[buttonIndex]
        val reach = Platform.player.blockReach
        if (Box3d.of(button).distanceSqr(Platform.player.eyePos) > reach * reach) {
            releaseAim()
            return
        }

        if (aim?.isEnded == true) releaseAim()
        val lease = aim ?: Platform.rotation.request(this, silent = true).also { aim = it }
        if (aimedButton != button) {
            lease.turnToPoint(Platform.world.blockAimPoint(button.x, button.y, button.z) ?: button.center(), rotationParams)
            aimedButton = button
        }
        if (!lease.isActive || lease.isTurning || tick < nextClickTick || pendingClick?.isPending == true) return

        pendingClick = Platform.interaction.submit(ClientAction.UseOn(button), owner = this) { result ->
            when (result) {
                ClientActionResult.SENT -> {
                    buttonIndex = (buttonIndex + 1) % buttons.size
                    nextClickTick = tick + CLICK_DELAY_TICKS
                }
                ClientActionResult.BLOCKED -> {
                    aimedButton = null
                    nextClickTick = tick + CLICK_DELAY_TICKS
                }
                else -> Unit
            }
        }
    }

    private fun releaseAim() {
        pendingClick?.cancel()
        pendingClick = null
        aim?.release()
        aim = null
        aimedButton = null
    }

    private fun reset() {
        releaseAim()
        buttonIndex = 0
        nextClickTick = 0
    }
}
