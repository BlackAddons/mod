package org.blackaddons.platform.impl.player.rotation
import net.minecraft.client.Minecraft
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.player.RotationHost
import org.blackaddons.api.util.TICKS_PER_SECOND
import org.blackaddons.api.world.Vec3d
import org.blackaddons.api.world.ViewAngles
import org.blackaddons.core.control.AimControl
import org.blackaddons.core.control.ViewPort
import org.blackaddons.platform.Installable
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.impl.player.MinecraftMovement
import org.blackaddons.platform.util.onClient

/**
 * [RotationHost]: [AimControl] driven by render frames and client ticks, over the camera and [MinecraftMovement]'s
 * silent slot.
 */
object MinecraftRotation : RotationHost by aim, Installable {

    override fun install() {
        EventListener(ClientTickEvent.Pre::class.java, EventPriority.HIGHEST) { aim.onTick() }.register()
        EventListener(WorldChangeEvent::class.java, EventPriority.NORMAL) { aim.onWorldChange() }.register()
    }

    fun onFrame(deltaTicks: Float) = aim.onFrame(deltaTicks)

    fun nudge(yawOffset: Float): Boolean = aim.nudge(yawOffset)

    fun holdThroughTick() = aim.holdThroughTick()
}

private val aim = AimControl(MinecraftViewPort)

private object MinecraftViewPort : ViewPort {
    private val mc get() = Minecraft.getInstance()

    override val inWorld: Boolean get() = mc.player != null
    override val screenOpen: Boolean get() = mc.currentScreen != null

    override val viewYaw: Float get() = mc.player?.yRot ?: 0f
    override val viewPitch: Float get() = mc.player?.xRot ?: 0f

    override fun setView(yaw: Float, pitch: Float) {
        val player = mc.player ?: return
        // A full turn in yRot would spin the camera on the next interpolation.
        val snappedYaw = MouseGrid.snap(player.yRot, ViewAngles.unwrap(yaw, near = player.yRot))
        player.yRot = snappedYaw
        player.xRot = MouseGrid.snap(player.xRot, pitch).coerceIn(-90f, 90f)
        player.yHeadRot = snappedYaw
        player.yBodyRot = snappedYaw
        MinecraftMovement.markRotationPending()
    }

    override val silentYaw: Float? get() = MinecraftMovement.silentYaw
    override val silentPitch: Float? get() = MinecraftMovement.silentPitch

    override fun setSilent(yaw: Float, pitch: Float) = MinecraftMovement.setSilentRotation(yaw, pitch)

    override fun clearSilent() = MinecraftMovement.discardSilentRotation()

    override fun silentToView() = MinecraftMovement.finishSilentRotation()

    override val eyePos: Vec3d
        get() = mc.player?.eyePosition?.let { Vec3d(it.x, it.y, it.z) } ?: Vec3d(0.0, 0.0, 0.0)

    override val eyeVelocity: Vec3d
        get() {
            val p = mc.player ?: return Vec3d(0.0, 0.0, 0.0)
            return Vec3d((p.x - p.xo) * TICKS_PER_SECOND, (p.y - p.yo) * TICKS_PER_SECOND, (p.z - p.zo) * TICKS_PER_SECOND)
        }

    override val partialTick: Float get() = mc.deltaTracker.getGameTimeDeltaPartialTick(false)

    override fun anglesTo(point: Vec3d): Pair<Float, Float> {
        val eye = mc.player?.eyePosition ?: return viewYaw to viewPitch
        return ViewAngles.between(Vec3d(eye.x, eye.y, eye.z), point)
    }

    override val cursorX: Double get() = mc.mouseHandler.xpos()
    override val cursorY: Double get() = mc.mouseHandler.ypos()

    override val turnStep: Float get() = MouseGrid.step

    override fun execute(block: () -> Unit) = onClient { block() }
}
