package org.blackaddons.platform.impl.player
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.component.UseEffects
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.input.ItemUseMovementChangeEvent
import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.player.DrivePriority
import org.blackaddons.api.player.Lease
import org.blackaddons.api.player.MoveKey
import org.blackaddons.api.player.MovementHost
import org.blackaddons.api.world.ViewAngles
import org.blackaddons.core.control.MovementControl
import org.blackaddons.platform.Installable
import org.blackaddons.platform.compat.NativeKeys
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.event.impl.PacketEvent
import org.blackaddons.platform.impl.player.rotation.MouseGrid
import org.blackaddons.platform.mixin.control.LocalPlayerAccessor
import org.blackaddons.platform.mixin.input.KeyMappingAccessor
import org.blackaddons.platform.mixin.network.ServerboundMovePlayerPacketAccessor
import org.blackaddons.platform.mixin.network.ServerboundUseItemPacketAccessor
import org.blackaddons.platform.util.onClient

object MinecraftMovement : MovementHost, Installable {
    private data class SilentRotation(val yaw: Float, val pitch: Float)

    private val mc get() = Minecraft.getInstance()
    private val player get() = mc.player

    @Volatile
    private var sprintBlocked = false

    @Volatile
    private var awaitingRotationSync = false

    @Volatile
    private var silentRotation: SilentRotation? = null

    private var earlyInput: Input? = null

    private val control = MovementControl()

    override val silentYaw: Float? get() = silentRotation?.yaw
    override val silentPitch: Float? get() = silentRotation?.pitch

    fun markRotationPending() {
        awaitingRotationSync = true
    }

    fun isAwaitingRotationSync(): Boolean = awaitingRotationSync

    fun onPositionSent() {
        awaitingRotationSync = false
        MinecraftInteraction.onPositionSent()
    }

    override fun install() {
        EventListener(PacketEvent.C2S.Pre::class.java, EventPriority.HIGHEST) {
            rewriteRotation(event)
        }.register()
        EventListener(ClientTickEvent.Pre::class.java, EventPriority.HIGHEST) { control.tick() }.register()
        EventListener(WorldChangeEvent::class.java, EventPriority.NORMAL) { control.onWorldChange() }.register()
    }

    override fun drive(owner: Any, priority: DrivePriority, steer: () -> Set<MoveKey>?): Lease =
        control.drive(owner, priority, steer)

    override fun hold(key: MoveKey, owner: Any): Lease = control.hold(key, owner)

    /** Called from `KeyboardInputMixin`, after the event's listeners have seen the player's own keys. */
    fun applyDrives(input: MovementInputEvent) = control.apply(input)

    // Only MinecraftRotation's aims write the silent slot.

    fun setSilentRotation(yaw: Float, pitch: Float) {
        val sentYaw = silentRotation?.yaw ?: player?.yRot ?: yaw
        val sentPitch = silentRotation?.pitch ?: player?.xRot ?: pitch
        val rotation = SilentRotation(
            MouseGrid.snap(sentYaw, ViewAngles.unwrap(yaw, near = sentYaw)),
            MouseGrid.snap(sentPitch, pitch.coerceIn(-90f, 90f)).coerceIn(-90f, 90f),
        )
        silentRotation = rotation
        awaitingRotationSync = true
        onClient {
            val p = player ?: return@onClient
            p.yHeadRot = rotation.yaw
            p.yHeadRotO = rotation.yaw
            p.yBodyRot = rotation.yaw
            p.yBodyRotO = rotation.yaw
        }
    }

    fun finishSilentRotation() = onClient {
        val rotation = silentRotation ?: return@onClient
        silentRotation = null
        awaitingRotationSync = true
        val p = player ?: return@onClient
        p.yRot = rotation.yaw
        p.yRotO = rotation.yaw
        p.xRot = rotation.pitch
        p.xRotO = rotation.pitch
        p.yHeadRot = rotation.yaw
        p.yHeadRotO = rotation.yaw
        p.yBodyRot = rotation.yaw
        p.yBodyRotO = rotation.yaw
    }

    fun discardSilentRotation() = onClient {
        if (silentRotation == null) return@onClient
        silentRotation = null
        awaitingRotationSync = true
        val p = player ?: return@onClient
        p.yHeadRot = p.yRot
        p.yHeadRotO = p.yRot
        p.yBodyRot = p.yRot
        p.yBodyRotO = p.yRot
    }

    override val velocityX: Double
        get() = player?.deltaMovement?.x ?: 0.0

    override val velocityZ: Double
        get() = player?.deltaMovement?.z ?: 0.0

    override val movementSpeed: Float
        get() = player?.speed ?: 0.1f

    override val surfaceSlipperiness: Float
        get() {
            val p = player ?: return 0.6f
            val level = mc.level ?: return 0.6f
            val groundPos = p.blockPosBelowThatAffectsMyMovement
            return level.getBlockState(groundPos).block.friction
        }

    override val frictionModifier: Float
        get() = player?.getAttributeValue(Attributes.FRICTION_MODIFIER)?.toFloat() ?: 1.0f

    override val airDragModifier: Float
        get() = player?.getAttributeValue(Attributes.AIR_DRAG_MODIFIER)?.toFloat() ?: 1.0f

    override val sneakingSpeed: Float
        get() = player?.getAttributeValue(Attributes.SNEAKING_SPEED)?.toFloat() ?: 0.3f

    override val isSprinting: Boolean
        get() = player?.isSprinting ?: false

    override val isClimbing: Boolean
        get() = player?.onClimbable() ?: false

    override val isInFluid: Boolean
        get() = player?.let { it.isInWater || it.isInLava } ?: false

    override val isFallFlying: Boolean
        get() = player?.isFallFlying ?: false

    override val isPassenger: Boolean
        get() = player?.isPassenger ?: false

    override val isCrouching: Boolean
        get() = player?.isCrouching ?: false

    override val isItemUseChangingSpeed: Boolean
        get() {
            val p = player ?: return false
            return p.isUsingItem &&
                p.useItem.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).speedMultiplier() != 1f
        }

    fun onStartUsingItem() {
        if (isItemUseChangingSpeed) EventBus.post(ItemUseMovementChangeEvent)
    }

    override val isScreenOpen: Boolean
        get() = mc.currentScreen != null

    override fun isKeyDown(key: MoveKey): Boolean {
        val options = mc.options
        val mapping = when (key) {
            MoveKey.FORWARD -> options.keyUp
            MoveKey.BACKWARD -> options.keyDown
            MoveKey.LEFT -> options.keyLeft
            MoveKey.RIGHT -> options.keyRight
            MoveKey.JUMP -> options.keyJump
            MoveKey.SNEAK -> options.keyShift
            MoveKey.SPRINT -> options.keySprint
        }
        return !mapping.isUnbound && NativeKeys.isDown((mapping as KeyMappingAccessor).`blackaddons$getBoundKey`())
    }

    override val isStillForServer: Boolean
        get() {
            val p = player ?: return true
            val sent = p.lastSentInput
            return !p.isSprinting && !sent.forward() && !sent.backward() && !sent.left() && !sent.right() &&
                !sent.jump() && !sent.shift()
        }

    override fun stopSprinting() = onClient {
        player?.isSprinting = false
    }

    override fun setSprintBlocked(blocked: Boolean) {
        sprintBlocked = blocked
    }

    @JvmStatic
    fun isSprintBlocked(): Boolean = sprintBlocked

    override fun sneakNow() = onClient {
        val p = player ?: return@onClient
        val sent = p.lastSentInput
        if (sent.shift()) return@onClient
        val sneaking = Input(sent.forward(), sent.backward(), sent.left(), sent.right(), sent.jump(), true, sent.sprint())
        p.connection.send(ServerboundPlayerInputPacket(sneaking))
        (p as LocalPlayerAccessor).`blackaddons$setLastSentInput`(sneaking)
        earlyInput = sneaking
    }

    @JvmStatic
    fun takeEarlyInput(): Input? = earlyInput.also { earlyInput = null }

    private fun rewriteRotation(event: PacketEvent.C2S.Pre) {
        val rotation = silentRotation ?: return
        when (val packet = event.packet) {
            is ServerboundMovePlayerPacket -> {
                if (!packet.hasRotation()) return
                (packet as ServerboundMovePlayerPacketAccessor).apply {
                    `blackaddons$setYaw`(rotation.yaw)
                    `blackaddons$setPitch`(rotation.pitch)
                }
            }
            is ServerboundUseItemPacket -> {
                (packet as Any as ServerboundUseItemPacketAccessor).apply {
                    `blackaddons$setYaw`(rotation.yaw)
                    `blackaddons$setPitch`(rotation.pitch)
                }
            }
        }
    }
}
