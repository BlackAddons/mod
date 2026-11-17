package org.blackaddons.platform.impl.player
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.level.GameType
import org.blackaddons.api.player.GameMode
import org.blackaddons.api.player.PlayerHost
import org.blackaddons.api.world.ItemInfo
import org.blackaddons.api.world.Vec3d
import org.blackaddons.platform.impl.world.MinecraftItems
import java.util.UUID

object MinecraftPlayer : PlayerHost {

    private val mc get() = Minecraft.getInstance()
    private val p get() = mc.player

    override val isInGame: Boolean get() = p != null

    override val name: String? get() = p?.gameProfile?.name
    override val uuid: UUID? get() = p?.uuid

    // Not gameProfile: the session profile has no skin. The server-filled tab entry is what MC renders us from.
    override val skinTextures: String?
        get() {
            val info = mc.connection?.getPlayerInfo(p?.uuid ?: return null) ?: return null
            return info.profile.properties["textures"].firstOrNull()?.value
        }

    override val x: Double get() = p?.x ?: 0.0
    override val y: Double get() = p?.y ?: 0.0
    override val z: Double get() = p?.z ?: 0.0

    override val eyePos: Vec3d get() = p?.let { Vec3d(it.x, it.eyeY, it.z) } ?: Vec3d(0.0, 0.0, 0.0)

    override val entityReach: Double get() = p?.entityInteractionRange() ?: super.entityReach
    override val blockReach: Double get() = p?.blockInteractionRange() ?: super.blockReach

    override val renderX: Double get() = p?.let { lerp(it.xo, it.x) } ?: 0.0
    override val renderY: Double get() = p?.let { lerp(it.yo, it.y) } ?: 0.0
    override val renderZ: Double get() = p?.let { lerp(it.zo, it.z) } ?: 0.0

    private fun lerp(old: Double, now: Double): Double {
        val t = mc.deltaTracker.getGameTimeDeltaPartialTick(false).toDouble()
        return old + (now - old) * t
    }

    override val yaw: Float get() = p?.yRot ?: 0f
    override val pitch: Float get() = p?.xRot ?: 0f

    override val renderHeadYaw: Float
        get() = p?.let { Mth.lerp(mc.deltaTracker.getGameTimeDeltaPartialTick(false), it.yHeadRotO, it.yHeadRot) } ?: 0f

    override val bodyYaw: Float get() = p?.yBodyRot ?: 0f

    override val height: Double get() = p?.bbHeight?.toDouble() ?: super.height

    override val width: Double get() = p?.bbWidth?.toDouble() ?: super.width

    override val stepHeight: Double get() = p?.maxUpStep()?.toDouble() ?: super.stepHeight

    override val isSneaking: Boolean get() = p?.isShiftKeyDown ?: false

    override val onGround: Boolean get() = p?.onGround() ?: false

    override val health: Float get() = p?.health ?: 0f
    override val maxHealth: Float get() = p?.maxHealth ?: 0f
    override val food: Int get() = p?.foodData?.foodLevel ?: 0

    override val gameMode: GameMode
        get() = when (mc.gameMode?.playerMode) {
            GameType.SURVIVAL -> GameMode.SURVIVAL
            GameType.CREATIVE -> GameMode.CREATIVE
            GameType.ADVENTURE -> GameMode.ADVENTURE
            GameType.SPECTATOR -> GameMode.SPECTATOR
            null -> GameMode.UNKNOWN
        }

    override val mainHandItem: ItemInfo? get() = p?.mainHandItem?.let(MinecraftItems::info)

    override val isFishing: Boolean get() = p?.fishing != null

    override fun inventoryItem(slot: Int): ItemInfo? {
        val inv = p?.inventory ?: return null
        if (slot < 0 || slot >= inv.containerSize) return null
        return MinecraftItems.info(inv.getItem(slot))
    }

    override val dimensionId: String?
        get() = mc.level?.dimension()?.identifier()?.toString()
}
