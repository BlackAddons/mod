package org.blackaddons.platform.impl.player

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.serialization.JsonOps
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.util.ProblemReporter
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.phys.*
import org.blackaddons.api.event.*
import org.blackaddons.api.event.world.BlockInteractEvent
import org.blackaddons.api.player.*
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.api.world.ViewAngles
import org.blackaddons.core.control.Holds
import org.blackaddons.core.interaction.ClickQueue
import org.blackaddons.core.interaction.ClickRule
import org.blackaddons.core.interaction.ClickVerdict
import org.blackaddons.core.interaction.InteractionReadiness
import org.blackaddons.platform.Installable
import org.blackaddons.platform.compat.NativeKeys
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.compat.swingLikeStartUseItem
import org.blackaddons.platform.event.impl.PacketEvent
import org.blackaddons.platform.impl.input.SyntheticKeys
import org.blackaddons.platform.impl.player.MinecraftInteraction.GRIM_YAW_FLOOR
import org.blackaddons.platform.impl.player.MinecraftInteraction.MAX_SETTLE
import org.blackaddons.platform.impl.player.MinecraftInteraction.MIN_SETTLE
import org.blackaddons.platform.impl.player.MinecraftInteraction.SETTLE_SPREAD
import org.blackaddons.platform.impl.player.MinecraftInteraction.clicks
import org.blackaddons.platform.impl.player.rotation.MinecraftRotation
import org.blackaddons.platform.impl.world.BlockHitboxShapes
import org.blackaddons.platform.mixin.input.KeyMappingAccessor
import org.blackaddons.platform.util.onClient
import org.blackaddons.platform.util.readOnClient
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

/**
 * [InteractionHost]. Clicks wait in [clicks] and get sent from the head of `Minecraft.handleKeybinds`
 * (`MinecraftMixin`), where real clicks happen: after `gameMode.tick()` flushed the hotbar slot, before the movement
 * packet, never with a screen open. Key clicks tap vanilla key mappings, targeted ones go through `MultiPlayerGameMode`
 * for sequence numbers. No hand-built packets.
 */
object MinecraftInteraction : InteractionHost, Installable {
    private val mc get() = Minecraft.getInstance()
    private val readiness = InteractionReadiness()

    private val holds = Holds(::applyHold)

    private val clicks = ClickQueue(
        listOf(
            /**
             * Targeted clicks wait until the server has the aim rotation, or, for on-contact clicks, a rotation that
             * already hits.
             */
            ClickRule { action, _ ->
                if (action.isTargeted && !canPerform() && !inContact(action)) ClickVerdict.WAIT else ClickVerdict.GO
            },
            ClickRule { action, _ ->
                if (action is ClientAction.UseOn && !readiness.canUseBlock()) ClickVerdict.WAIT else ClickVerdict.GO
            },
            ClickRule { action, _ -> if (settleFirst(action)) ClickVerdict.WAIT else ClickVerdict.GO },
            // startUseItem silently drops a use while a block is being broken.
            ClickRule { action, _ ->
                if (action == ClientAction.Use && mc.gameMode?.isDestroying == true) ClickVerdict.WAIT else ClickVerdict.GO
            },
        ),
    )

    @Volatile
    override var settleBeforeClicks = false

    /** Grim's DuplicateRotPlace ignores yaw changes up to this many degrees. */
    private const val GRIM_YAW_FLOOR = 2f

    /** Above a mouse step, safely under [GRIM_YAW_FLOOR]. */
    private const val MIN_SETTLE = 0.25f
    private const val MAX_SETTLE = 1.5f

    /** Share of the target's apparent width a settle can move within. */
    private const val SETTLE_SPREAD = 0.6f

    /** Waiting for an in-flight settle instead of settling again. */
    private var settling = false

    override fun install() {
        EventListener(ClientTickEvent.Pre::class.java, EventPriority.HIGHEST) {
            ticks++
            readiness.onTickStart()
            // A click decided before a screen opened shouldn't land on whatever is targeted after it closes.
            if (mc.currentScreen != null) clicks.clear(ClientActionResult.EXPIRED) else clicks.tick()
            holds.tick()
        }.register()
        EventListener(PacketEvent.C2S.Post::class.java, EventPriority.NORMAL) {
            val packet = event.packet
            if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
                readiness.onRotationSent(packet.getYRot(0f), packet.getXRot(0f))
            }
            if (packet is ServerboundUseItemOnPacket) readiness.onBlockUseSent()
        }.register()
        EventListener(WorldChangeEvent::class.java, EventPriority.NORMAL) {
            readiness.reset()
            clicks.clear()
            holds.clear()
            settling = false
        }.register()
    }

    override val crosshairBlock: BlockPos3?
        get() {
            val hit = mc.hitResult ?: return null
            if (hit.type != HitResult.Type.BLOCK) return null
            return (hit as BlockHitResult).blockPos.toBlockPos3()
        }

    override val crosshairEntity: Boolean
        get() = mc.hitResult?.type == HitResult.Type.ENTITY

    override val clickTargetBlock: BlockPos3?
        get() = readOnClient { (clickHitResult() as? BlockHitResult)?.blockPos?.toBlockPos3() }

    override val clickTargetEntity: Boolean
        get() = readOnClient { clickHitResult()?.type == HitResult.Type.ENTITY }

    override val sentTargetBlock: BlockPos3?
        get() = readOnClient { sentBlockHit()?.blockPos?.toBlockPos3() }

    override val crosshairEntityId: UUID?
        get() = (mc.hitResult as? EntityHitResult)?.entity?.uuid

    override fun crosshairEntityRaw(): String? {
        val entity = (mc.hitResult as? EntityHitResult)?.entity ?: return null
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess())
        output.putString("id", EntityType.getKey(entity.type).toString())
        entity.saveWithoutId(output)
        (entity as? Player)?.gameProfile?.let { profile ->
            val profileOutput = output.child("Profile")
            profileOutput.putString("name", profile.name())
            profileOutput.putString("id", profile.id().toString())
            profile.properties().values().forEach { property ->
                val value = if (property.name() == "textures") {
                    runCatching { String(Base64.getDecoder().decode(property.value())) }.getOrDefault(property.value())
                } else {
                    property.value()
                }
                profileOutput.putString(property.name(), value)
            }
        }
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, output.buildResult()).toString()
    }

    override val clickTargetEntityId: UUID?
        get() = readOnClient { (clickHitResult() as? EntityHitResult)?.entity?.uuid }

    override val selectedSlot: Int
        get() = mc.player?.inventory?.selectedSlot ?: 0

    override fun selectHotbarSlot(slot: Int) {
        if (slot !in 0..8) return
        onClient { mc.player?.inventory?.selectedSlot = slot }
    }

    override fun submit(action: ClientAction, owner: Any?, onResult: (ClientActionResult) -> Unit): ClickRequest =
        clicks.submit(action, owner, onResult)

    override fun cancel(owner: Any) = clicks.cancel(owner)

    /** Marks interaction events from our own clicks. */
    private var sending = false

    private var ticks = 0

    /**
     * Use-key taps that haven't turned into a block use yet, and the last tap's tick. Consumed in the same or next
     * `handleKeybinds` pass. A tap on air goes stale after a tick so it can't claim a later player click.
     */
    private var tappedUses = 0
    private var lastUseTapTick = 0

    /** Whether the current block use came from this host. */
    fun consumeSyntheticUse(): Boolean {
        if (sending) return true
        if (tappedUses == 0) return false
        if (ticks - lastUseTapTick > 1) {
            tappedUses = 0
            return false
        }
        tappedUses--
        return true
    }

    /** Only called from the head of `Minecraft.handleKeybinds`. */
    fun sendQueued() = clicks.drain { action ->
        sending = true
        try {
            send(action).also { if (it == ClientActionResult.SENT && settleBeforeClicks) MinecraftRotation.holdThroughTick() }
        } finally {
            sending = false
        }
    }

    override fun hold(button: MouseButton, owner: Any): Lease = holds.hold(button, owner)

    override fun pressKey(name: String): Lease? = SyntheticKeys.press(name)

    private fun applyHold(button: MouseButton, held: Boolean) = onClient {
        when (button) {
            MouseButton.USE -> hold(mc.options.keyUse, held)
            MouseButton.ATTACK -> {
                val key = mc.options.keyAttack
                val acc = key as KeyMappingAccessor
                // A hold from nothing counts as a press, so the first swing lands now.
                if (held && !key.isDown && !isPhysicalKeyDown(acc.`blackaddons$getBoundKey`()) &&
                    acc.`blackaddons$getClickCount`() == 0
                ) {
                    acc.`blackaddons$setClickCount`(1)
                }
                hold(key, held)
            }
        }
    }

    fun shouldDeferAttack(): Boolean {
        if (!MinecraftMovement.isAwaitingRotationSync()) return false
        readiness.deferAttack()
        return true
    }

    fun shouldDeferUse(): Boolean {
        if (MinecraftMovement.silentYaw == null || MinecraftMovement.silentPitch == null) return false
        if (canPerform() && readiness.canUseBlock()) return false
        readiness.deferUse()
        return true
    }

    fun shouldDeferContinuousAttack(): Boolean = MinecraftMovement.isAwaitingRotationSync()

    /** Held attack on a block. True if a [BlockInteractEvent.Mine] listener stopped it, after the break was aborted. */
    fun stopsMining(): Boolean {
        val hit = mc.hitResult as? BlockHitResult ?: return false
        if (hit.type != HitResult.Type.BLOCK) return false
        val pos = hit.blockPos
        if (!EventBus.post(BlockInteractEvent.Mine(pos.x, pos.y, pos.z)).cancelled) return false
        mc.gameMode?.stopDestroyBlock()
        return true
    }

    fun onPositionSent() {
        settling = false
        val deferred = readiness.onPositionSent()
        // Next handleKeybinds pass. Sending now would follow this tick's movement packet.
        if (deferred.attack) tap(mc.options.keyAttack)
        if (deferred.use) {
            if (MinecraftMovement.silentYaw != null && MinecraftMovement.silentPitch != null) {
                tap(mc.options.keyUse)
            }
        }
    }

    fun raycastSilent(yaw: Float, pitch: Float): HitResult? {
        val player = mc.player ?: return null
        val realYaw = player.yRot
        val realPitch = player.xRot
        return try {
            player.yRot = yaw
            player.xRot = pitch
            player.raycastHitResult(1.0f, player)
        } finally {
            player.yRot = realYaw
            player.xRot = realPitch
        }
    }

    /**
     * See [settleBeforeClicks]. Grim ignores yaw changes under [GRIM_YAW_FLOOR], so a smaller settle never gets
     * checked. No aim or no fitting turn: click as is.
     */
    private fun settleFirst(action: ClientAction): Boolean {
        if (!settleBeforeClicks) return false
        if (settling) return true
        if (readiness.lastYawChange <= GRIM_YAW_FLOOR) return false
        val hit = clickHitResult()
        val target = targetOf(hit, action) ?: return false
        val box = boxOf(hit) ?: return false
        val yaw = MinecraftMovement.serverYaw
        val offset = settleOffset(yaw, box) ?: return false
        if (targetOf(raycastSilent(yaw + offset, MinecraftMovement.serverPitch), action) != target) return false
        if (!MinecraftRotation.nudge(offset)) return false
        settling = true
        return true
    }

    /**
     * Random yaw change within the middle [SETTLE_SPREAD] of [box]'s yaw band, clamped to [MIN_SETTLE]..[MAX_SETTLE]
     * and at least a mouse step. Null if none fits.
     */
    private fun settleOffset(yaw: Float, box: AABB): Float? {
        val eye = mc.player?.eyePosition?.let { Vec3d(it.x, it.y, it.z) } ?: return null
        val corners = listOf(box.minX to box.minZ, box.minX to box.maxZ, box.maxX to box.minZ, box.maxX to box.maxZ)
        val offsets = corners.map { (x, z) -> ViewAngles.unwrap(ViewAngles.between(eye, Vec3d(x, eye.y, z)).first, near = yaw) - yaw }
        val center = (offsets.min() + offsets.max()) / 2
        val half = (offsets.max() - offsets.min()) / 2 * SETTLE_SPREAD
        val min = maxOf(MinecraftRotation.turnStep, MIN_SETTLE)
        val sides = listOf(
            maxOf(center - half, min)..minOf(center + half, MAX_SETTLE),
            maxOf(center - half, -MAX_SETTLE)..minOf(center + half, -min)
        )
            .filter { it.start < it.endInclusive }
        val side = sides.randomOrNull() ?: return null
        return side.start + Random.nextFloat() * (side.endInclusive - side.start)
    }

    /** Real shape, not `getShape`: an enlarged hitbox could put the settle on the enlarged part. */
    private fun boxOf(hit: HitResult?): AABB? = when (hit) {
        is BlockHitResult -> mc.level?.let { level ->
            BlockHitboxShapes.real(level.getBlockState(hit.blockPos), level, hit.blockPos)
                .takeUnless { it.isEmpty }?.bounds()?.move(hit.blockPos)
        }
        is EntityHitResult -> hit.entity.boundingBox.inflate(hit.entity.pickRadius.toDouble())
        else -> null
    }

    private fun targetOf(hit: HitResult?, action: ClientAction): Any? = when {
        hit is BlockHitResult && hit.type == HitResult.Type.BLOCK ->
            hit.blockPos.takeIf { action !is ClientAction.UseOn || it == BlockPos(action.block.x, action.block.y, action.block.z) }
        hit is EntityHitResult -> hit.entity.uuid.takeIf { action !is ClientAction.Interact || it == action.entity }
        else -> null
    }

    private val ClientAction.isTargeted get() = this is ClientAction.Interact || this is ClientAction.UseOn

    private fun canPerform(): Boolean = readiness.canPerform(
        MinecraftMovement.serverYaw, MinecraftMovement.serverPitch, MinecraftMovement.isAwaitingRotationSync(),
    )

    private fun send(action: ClientAction): ClientActionResult = when (action) {
        ClientAction.Use -> tapped(mc.options.keyUse)
        ClientAction.Attack -> tapped(mc.options.keyAttack)
        ClientAction.UseItem -> usedItem()
        is ClientAction.Interact, is ClientAction.UseOn -> sendTargeted(action)
    }

    // Consumed by the handleKeybinds pass that's just starting.
    private fun tapped(key: KeyMapping): ClientActionResult {
        if (mc.player == null) return ClientActionResult.BLOCKED
        tap(key)
        return ClientActionResult.SENT
    }

    // Server rotation, because MinecraftMovement rewrites it during silent aim.
    private fun usedItem(): ClientActionResult {
        val player = mc.player ?: return ClientActionResult.BLOCKED
        val gameMode = mc.gameMode ?: return ClientActionResult.BLOCKED
        player.swingLikeStartUseItem(gameMode.useItem(player, InteractionHand.MAIN_HAND))
        return ClientActionResult.SENT
    }

    private fun sendTargeted(action: ClientAction): ClientActionResult {
        val player = mc.player ?: return ClientActionResult.BLOCKED
        val gameMode = mc.gameMode ?: return ClientActionResult.BLOCKED
        val reach = when (action) {
            is ClientAction.UseOn -> player.blockInteractionRange()
            else -> player.entityInteractionRange()
        }
        val (blockHit, entityHit) = serverHits(player, reach) ?: return ClientActionResult.BLOCKED
        val result = when (action) {
            is ClientAction.Interact -> {
                if (entityHit?.entity?.uuid != action.entity) return ClientActionResult.BLOCKED
                gameMode.interact(player, entityHit.entity, entityHit, InteractionHand.MAIN_HAND)
            }
            is ClientAction.UseOn -> {
                if (!hitsBlock(action, blockHit, entityHit)) return ClientActionResult.BLOCKED
                gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit)
            }
            else -> return ClientActionResult.BLOCKED
        }
        player.swingLikeStartUseItem(result)
        return ClientActionResult.SENT
    }

    private fun inContact(action: ClientAction): Boolean =
        action is ClientAction.UseOn && action.onContact &&
            sentBlockHit()?.blockPos == BlockPos(action.block.x, action.block.y, action.block.z)

    private fun sentBlockHit(): BlockHitResult? {
        if (readiness.clickRotation() == null) return null
        val player = mc.player ?: return null
        val (blockHit, entityHit) = serverHits(player, player.blockInteractionRange()) ?: return null
        return blockHit.takeIf { it.type == HitResult.Type.BLOCK && entityHit == null }
    }

    private fun serverHits(player: Player, reach: Double): Pair<BlockHitResult, EntityHitResult?>? {
        val level = mc.level ?: return null
        val (from, to) = serverRay(player, reach)
        val blockHit = level.clip(ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        val blockDistance = if (blockHit.type == HitResult.Type.BLOCK) from.distanceToSqr(blockHit.location)
        else reach * reach
        val entityHit = ProjectileUtil.getEntityHitResult(
            player, from, to, player.boundingBox.expandTowards(to.subtract(from)).inflate(1.0),
            { it.isPickable && !it.isSpectator }, blockDistance,
        )
        return blockHit to entityHit
    }

    private fun hitsBlock(action: ClientAction.UseOn, blockHit: BlockHitResult, entityHit: EntityHitResult?): Boolean =
        blockHit.type == HitResult.Type.BLOCK && entityHit == null &&
            blockHit.blockPos == BlockPos(action.block.x, action.block.y, action.block.z)

    // The rotation the server checks a click against: the last one sent, which equals a settled aim.
    private fun serverRay(player: Player, reach: Double): Pair<Vec3, Vec3> {
        val (yaw, pitch) = readiness.clickRotation() ?: (MinecraftMovement.serverYaw to MinecraftMovement.serverPitch)
        val direction = Vec3.directionFromRotation(pitch, yaw)
        return player.eyePosition to player.eyePosition.add(direction.scale(reach))
    }

    private fun clickHitResult(): HitResult? {
        val yaw = MinecraftMovement.silentYaw ?: return mc.hitResult
        val pitch = MinecraftMovement.silentPitch ?: return mc.hitResult
        return raycastSilent(yaw, pitch)
    }

    private fun BlockPos.toBlockPos3() = BlockPos3(x, y, z)

    private fun tap(key: KeyMapping) {
        if (key === mc.options.keyUse) {
            tappedUses++
            lastUseTapTick = ticks
        }
        val acc = key as KeyMappingAccessor
        val wasDown = key.isDown
        acc.`blackaddons$setDown`(true)
        acc.`blackaddons$setClickCount`(acc.`blackaddons$getClickCount`() + 1)
        acc.`blackaddons$setDown`(wasDown)
    }

    private fun hold(key: KeyMapping, held: Boolean) {
        val acc = key as KeyMappingAccessor
        val bound = acc.`blackaddons$getBoundKey`()
        val down = held || isPhysicalKeyDown(bound)
        KeyMapping.set(bound, down)
        acc.`blackaddons$setDown`(down)
    }

    private fun isPhysicalKeyDown(key: InputConstants.Key?): Boolean = key != null && NativeKeys.isDown(key)
}
