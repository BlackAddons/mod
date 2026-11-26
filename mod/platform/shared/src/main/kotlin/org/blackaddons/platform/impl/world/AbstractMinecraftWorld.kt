package org.blackaddons.platform.impl.world

import com.mojang.serialization.Dynamic
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.util.datafix.fixes.BlockStateData
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.*
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.blackaddons.api.event.world.EntitySnapshot
import org.blackaddons.api.player.BlockFace
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.world.*
import org.blackaddons.platform.compat.redrawWorld
import org.blackaddons.platform.mixin.world.BlockItemInvoker
import org.blackaddons.platform.util.*
import java.util.UUID

/**
 * [WorldHost] over `Minecraft.getInstance().level`, safe defaults without a world. Only the day-time accessor differs
 * per version ([dayTime]).
 *
 * **Anything iterating live Minecraft structures goes through [readOnClient]**: entity and chunk stores mutate on tick.
 * Single null-safe field reads stay direct, a hop would cost more than it buys.
 */
abstract class AbstractMinecraftWorld : WorldHost {

    protected val mc get() = Minecraft.getInstance()

    private fun blockCatalog(): List<BlockCatalogEntry> {
        val creativeOrder = creativeOrder()
        return BuiltInRegistries.BLOCK
            .mapNotNull { block ->
                val item = when (block) {
                    Blocks.WATER -> Items.WATER_BUCKET
                    Blocks.LAVA -> Items.LAVA_BUCKET
                    Blocks.FIRE -> Items.FIRE_CHARGE
                    Blocks.SOUL_FIRE -> Items.SOUL_TORCH
                    else -> block.asItem()
                }
                if (item === Items.AIR) return@mapNotNull null
                BlockCatalogEntry(
                    BuiltInRegistries.BLOCK.getKey(block).toString(),
                    BuiltInRegistries.ITEM.getKey(item).toString(),
                ) to (creativeOrder[item] ?: Int.MAX_VALUE)
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun creativeOrder(): Map<Item, Int> {
        val player = mc.player ?: return emptyMap()
        val holders = player.level().registryAccess()
        if (CreativeModeTabs.tryRebuildTabContents(player.connection.enabledFeatures(), true, holders)) {
            /* The creative screen only refreshes search trees when it rebuilds them itself. */
            val searchItems = CreativeModeTabs.searchTab().displayItems.toList()
            player.connection.searchTrees().updateCreativeTooltips(holders, searchItems)
            player.connection.searchTrees().updateCreativeTags(searchItems)
        }
        val order = HashMap<Item, Int>()
        CreativeModeTabs.tabs().filter { it.type == CreativeModeTab.Type.CATEGORY }.forEach { tab ->
            tab.displayItems.forEach { order.putIfAbsent(it.item, order.size) }
        }
        return order
    }

    override val isLoaded: Boolean get() = mc.level != null

    override val dimensionId: String?
        get() = mc.level?.dimension()?.identifier()?.toString()

    override val serverAddress: String?
        get() = mc.currentServer?.ip

    override val serverBrand: String?
        get() = mc.connection?.serverBrand()

    override val isSingleplayer: Boolean get() = mc.isLocalServer

    override val gameTime: Long get() = mc.level?.gameTime ?: 0L

    override fun blockIdAt(x: Int, y: Int, z: Int): String? = readOnClient {
        val level = mc.level ?: return@readOnClient null
        val block = level.getBlockState(BlockPos(x, y, z)).block
        BuiltInRegistries.BLOCK.getKey(block).toString()
    }

    override fun blockAimPoint(x: Int, y: Int, z: Int): Vec3d? = readOnClient {
        val level = mc.level ?: return@readOnClient null
        val pos = BlockPos(x, y, z)
        // getShape is block-local, so offset by the position. Empty shapes have no bounds.
        val shape = outline(level, pos)
        if (shape.isEmpty) return@readOnClient null
        val centre = shape.bounds().center
        Vec3d(x + centre.x, y + centre.y, z + centre.z)
    }

    /** Vanilla's own shape, used inside [BlockHitboxes.vanilla]. */
    private fun outline(level: BlockGetter, pos: BlockPos) = level.getBlockState(pos).let { state ->
        if (BlockHitboxes.realShapes) BlockHitboxShapes.real(state, level, pos) else state.getShape(level, pos)
    }

    override fun blockOutlineBoxes(x: Int, y: Int, z: Int): List<Box3d> = readOnClient {
        val level = mc.level ?: return@readOnClient emptyList()
        val pos = BlockPos(x, y, z)
        outline(level, pos).toAabbs().map { box ->
            Box3d(
                Vec3d(x + box.minX, y + box.minY, z + box.minZ),
                Vec3d(x + box.maxX, y + box.maxY, z + box.maxZ),
            )
        }
    }

    override fun snapshot(corner: BlockPos3, opposite: BlockPos3): TerrainSnapshot? = readOnClient {
        val level = mc.level ?: return@readOnClient null
        Terrain.snapshot(
            level,
            minOf(corner.x, opposite.x), minOf(corner.y, opposite.y), minOf(corner.z, opposite.z),
            maxOf(corner.x, opposite.x), maxOf(corner.y, opposite.y), maxOf(corner.z, opposite.z),
        )
    }

    override fun chunkSnapshot(chunkX: Int, chunkZ: Int): TerrainSnapshot? = readOnClient {
        mc.level?.let { Terrain.chunkSnapshot(it, chunkX, chunkZ) }
    }

    override fun collides(box: Box3d): Boolean = readOnClient {
        val level = mc.level ?: return@readOnClient false
        val aabb = AABB(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z)
        !level.noCollision(mc.player, aabb)
    }

    override fun blockProperty(x: Int, y: Int, z: Int, name: String): String? = readOnClient {
        val state = mc.level?.getBlockState(BlockPos(x, y, z)) ?: return@readOnClient null
        state.propertyValue(name)
    }

    override fun skullOwnerId(x: Int, y: Int, z: Int): String? = readOnClient {
        val skull = mc.level?.getBlockEntity(BlockPos(x, y, z)) as? SkullBlockEntity ?: return@readOnClient null
        skull.ownerProfile?.partialProfile()?.id()?.toString()
    }

    override fun entitiesNear(x: Double, y: Double, z: Double, radius: Double): List<EntitySnapshot> =
        readOnClient {
            val level = mc.level ?: return@readOnClient emptyList()
            val box = AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)
            // Snapshot inside the hop. Reading live entities afterwards brings the race right back.
            level.getEntities(null as Entity?, box).map { EntitySnapshots.of(it) }
        }

    override fun entityBox(uuid: UUID): Box3d? = readOnClient {
        val box = mc.level?.getEntity(uuid)?.boundingBox ?: return@readOnClient null
        Box3d(Vec3d(box.minX, box.minY, box.minZ), Vec3d(box.maxX, box.maxY, box.maxZ))
    }

    override fun playerUuid(name: String): UUID? = readOnClient {
        mc.level?.players()?.firstOrNull { it.gameProfile.name().equalsIgnoreCase(name) }?.uuid
    }

    override fun playerRotation(name: String): Pair<Float, Float>? = readOnClient {
        val player = mc.level?.players()?.firstOrNull { it.gameProfile.name().equalsIgnoreCase(name) } ?: return@readOnClient null
        player.getViewYRot(1f) to player.getViewXRot(1f)
    }

    override fun headItem(uuid: UUID): ItemInfo? = readOnClient {
        val entity = mc.level?.getEntity(uuid) as? LivingEntity ?: return@readOnClient null
        MinecraftItems.info(entity.getItemBySlot(EquipmentSlot.HEAD))
    }

    override fun itemFrames(box: Box3d): List<ItemFrameSnapshot> = readOnClient {
        val level = mc.level ?: return@readOnClient emptyList()
        val area = AABB(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z)
        level.getEntitiesOfClass(ItemFrame::class.java, area).map { frame ->
            val pos = frame.blockPosition()
            ItemFrameSnapshot(
                frame.uuid,
                BlockPos3(pos.x, pos.y, pos.z),
                frame.rotation,
                frame.item.takeUnless { it.isEmpty }?.let { BuiltInRegistries.ITEM.getKey(it.item).toString() },
            )
        }
    }

    override val onlinePlayerNames: List<String>
        get() = readOnClient {
            mc.connection?.onlinePlayers.orEmpty()
                .mapNotNull { it.profile.name }
                .filter { ACCOUNT_NAME.matches(it) }
                .sorted()
        }

    override val onlinePlayerUuids: List<UUID>
        get() = readOnClient {
            mc.connection?.onlinePlayers.orEmpty()
                .map { it.profile.id }
                .filter(Uuids::isAccount)
        }

    override val registeredParticleIds: List<String>
        get() = readOnClient {
            BuiltInRegistries.PARTICLE_TYPE.keySet().map { it.toString() }.sorted()
        }

    override val registeredBlocks: List<BlockCatalogEntry>
        get() = readOnClient(::blockCatalog)

    override fun placementAt(blockId: String, pos: BlockPos3, face: BlockFace, point: Vec3d): BlockPlacement? = readOnClient {
        val player = mc.player ?: return@readOnClient null
        val block = blockById(blockId) ?: return@readOnClient null
        val item = block.asItem()
        val hit = BlockHitResult(Vec3(point.x, point.y, point.z), Direction.valueOf(face.name), BlockPos(pos.x, pos.y, pos.z), false)
        // replaceClicked fills [pos] itself: FME targets real blocks or real air behind visual ones, not the neighbor.
        val context = object : BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack(item), hit) {
            init {
                replaceClicked = true
            }
        }
        val state = (item as? BlockItem)?.let { (it as BlockItemInvoker).`blackaddons$getPlacementState`(context) }
            ?: block.getStateForPlacement(context)
            ?: return@readOnClient null
        BlockPlacement(BuiltInRegistries.BLOCK.getKey(state.block).toString(), state.changedProperties())
    }

    override fun rotateProperties(blockId: String, properties: Map<String, String>, quarterTurns: Int): Map<String, String> {
        val turns = Math.floorMod(quarterTurns, 4)
        if (turns == 0) return properties
        val block = blockById(blockId) ?: return properties
        return block.defaultBlockState().withProperties(properties).rotate(Rotation.entries[turns]).changedProperties()
    }

    override fun flattenLegacyBlock(state: String): BlockPlacement? {
        val name = state.substringBefore('[').trim()
        val properties = state.substringAfter('[', "").substringBefore(']').split(',')
            .filter { '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val legacy = Dynamic(NbtOps.INSTANCE, CompoundTag().apply {
            putString("Name", name)
            if (properties.isNotEmpty()) put("Properties", CompoundTag().apply { properties.forEach(::putString) })
        })
        val upgraded = BlockStateData.upgradeBlockStateTag(legacy).takeUnless { it === legacy }
        val block = blockById(upgraded?.get("Name")?.asString("") ?: BlockStateData.upgradeBlock(name)) ?: return null
        val values = upgraded?.get("Properties")?.asMap({ it.asString("") }, { it.asString("") }) ?: properties
        val flattened = block.defaultBlockState().withProperties(values)
        return BlockPlacement(BuiltInRegistries.BLOCK.getKey(flattened.block).toString(), flattened.changedProperties())
    }

    /** Only the sections the block and its neighbors touch, like vanilla. */
    override fun redrawBlock(x: Int, y: Int, z: Int) {
        mc.execute { SectionRedraws.block(x, y, z) }
    }

    override fun redrawChunk(chunkX: Int, chunkZ: Int) {
        mc.execute {
            val level = mc.level ?: return@execute
            SectionRedraws.chunk(level, chunkX, chunkZ)
        }
    }

    /**
     * Skips sections whose palette can't hold [blockIds], wall variants count as their standing block. Neighbors get
     * dirtied too, because their culling reads across the border.
     */
    override fun redrawBlockTypes(blockIds: Set<String>) {
        val blocks = blockIds.mapNotNullTo(HashSet(), ::blockById)
        if (blocks.isEmpty()) return
        mc.execute {
            val player = mc.player ?: return@execute
            SectionRedraws.types(
                blocks,
                SectionPos.blockToSectionCoord(player.blockX),
                SectionPos.blockToSectionCoord(player.blockZ),
                mc.options.getEffectiveRenderDistance() + 1,
            )
        }
    }

    override fun redrawWorld() {
        mc.execute { mc.redrawWorld() }
    }

    private companion object {
        /** Valid Mojang names, Hypixel's decorative tab slots don't match. */
        val ACCOUNT_NAME = Regex("\\w{3,16}")
    }
}
