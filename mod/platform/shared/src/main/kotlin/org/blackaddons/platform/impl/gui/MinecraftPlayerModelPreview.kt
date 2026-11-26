package org.blackaddons.platform.impl.gui
import com.google.common.collect.LinkedHashMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.item.ItemStack
import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.Scale
import org.blackaddons.api.gui.PlayerModelPreview
import org.blackaddons.api.gui.PreviewGear
import org.blackaddons.api.util.Uuids
import org.blackaddons.blackskija.api.draw.SkijaEntity
import org.blackaddons.blackvertex.api.attach.AttachmentPoint
import org.blackaddons.core.cosmetics.PlayerScale
import org.blackaddons.platform.cosmetics.Cosmetic3dManager
import org.blackaddons.platform.cosmetics.PlayerCustomization
import org.blackaddons.platform.impl.gui.MinecraftPlayerModelPreview.endFrame
import org.blackaddons.platform.impl.gui.MinecraftPlayerModelPreview.record
import org.blackaddons.platform.impl.gui.MinecraftPlayerModelPreview.request
import org.blackaddons.platform.ui.render.ItemStacks
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Player preview for the cosmetic editor, wardrobe and Profile Viewer, at an explicit yaw/pitch, framed in the caller's
 * box.
 *
 * [request] sets the [PlayerScale] override, builds a [GuiEntityRenderState] and queues it on [SkijaEntity] right away,
 * so the model composites over the panel in the caller's paint order. [endFrame] only does housekeeping. Framing reads
 * the scale from [Platform.cosmetics], matching what the mixin draws.
 */
object MinecraftPlayerModelPreview : PlayerModelPreview {

    private data class Request(
        val x0: Int, val y0: Int, val x1: Int, val y1: Int,
        val yaw: Float, val pitch: Float, val at: TimeSource.Monotonic.ValueTimeMark,
        /** Null means the local player. */
        val target: Target?,
        val focusOnCosmetic: Boolean = false,
        val showNameTag: Boolean = false,
        val gear: PreviewGear? = null,
    )

    /**
     * Camera aim, zoom and starting yaw in model units (stands on y = 0). [yaw] is added to the caller's, so a tail can
     * face the viewer.
     */
    private data class Focus(val height: Float, val zoom: Float, val yaw: Float = 0f)

    /** Off-world player for the Profile Viewer. */
    private data class Target(
        val uuid: UUID, val name: String, val textures: String?, val signature: String?,
        /** Bundled skin override (wardrobe mannequins). */
        val skinTexture: String? = null,
    )

    // Keyed by the caller's key, a wardrobe asks for a lot per frame. Render thread only.
    private val pending = LinkedHashMap<Any, Request>()

    // Reused client-only fake players, never added to the world.
    private val fakePlayers = HashMap<UUID, RemotePlayer>()

    /**
     * getSkin() only checks the tab list by UUID, so off-server profiles would get the default. Resolve via SkinManager
     * and force it on the render state.
     */
    private val fakeSkins = HashMap<UUID, Supplier<PlayerSkin>>()

    private val bundledSkins = HashMap<String, PlayerSkin>()

    override fun request(
        x: Float, y: Float, w: Float, h: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float,
        yawDeg: Float, pitchDeg: Float,
        key: Any,
        showNameTag: Boolean,
    ) {
        PlayerScale.setPreview(Scale(scaleX, scaleY, scaleZ))
        record(key, Request(
            x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt(),
            yawDeg, pitchDeg, TimeSource.Monotonic.markNow(), target = null,
            showNameTag = showNameTag,
        ))
    }

    override fun requestPlayer(
        uuid: String, name: String, textures: String?, signature: String?,
        x: Float, y: Float, w: Float, h: Float,
        yawDeg: Float, pitchDeg: Float,
        key: Any,
        focusOnCosmetic: Boolean,
        skinTexture: String?,
        showNameTag: Boolean,
        gear: PreviewGear?,
    ) {
        val id = Uuids.parse(uuid) ?: return
        // The real UUID shows a size cosmetic, and framing reads the same scale.
        record(key, Request(
            x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt(),
            yawDeg, pitchDeg, TimeSource.Monotonic.markNow(),
            target = Target(id, name, textures, signature, skinTexture),
            focusOnCosmetic = focusOnCosmetic,
            showNameTag = showNameTag,
            gear = gear,
        ))
    }

    /** Queues the draw here so the model keeps caller paint order. A deferred pass drew all models over the UI. */
    private fun record(key: Any, request: Request) {
        pending[key] = request
        drawNow(key, request)
    }

    override fun clear() {
        pending.clear()
        fakePlayers.clear()
        fakeSkins.clear()
        PlayerScale.setPreview(null)
        Cosmetic3dManager.setPreviewTargets(emptySet())
    }

    /** Drops stale previews and tells the renderer who's being previewed. Drawing happens in [record]. */
    fun endFrame() {
        // Unrequested previews expire: closed screens and scrolled-off tiles free themselves.
        pending.values.removeIf { it.at.elapsedNow() > FRESH }
        // Previewed players might not be in the tab list, and 3D cosmetics only track known players.
        val previewed = pending.values.mapNotNullTo(HashSet()) { it.target?.uuid }
        Cosmetic3dManager.setPreviewTargets(previewed)
        fakePlayers.keys.retainAll(previewed)
        fakeSkins.keys.retainAll(previewed)
    }

    private fun drawNow(key: Any, req: Request) {
        val player = playerFor(req.target) ?: return
        // A fake player is shared by every preview of that profile, so each one dresses it for its own draw.
        if (req.target != null) dress(player, req.gear)
        // Needed before extract, because the focus sets the starting yaw.
        val focus = if (req.focusOnCosmetic) focusOf(player.uuid) else null
        val rs = extractAtYaw(player, req.yaw + (focus?.yaw ?: 0f))
        if (!req.showNameTag) rs.nameTag = null

        // Fake players get no skin layers (bare head). Force them on, the editor's local player keeps its options.
        if (req.target != null) (rs as? AvatarRenderState)?.let {
            it.showHat = true; it.showJacket = true
            it.showLeftSleeve = true; it.showRightSleeve = true
            it.showLeftPants = true; it.showRightPants = true
            // Swap the extract's default skin. The supplier gives the default until downloaded, so re-read per frame.
            val bundled = req.target.skinTexture?.let(::bundledSkin)
            if (bundled != null) it.skin = bundled
            else fakeSkins[req.target.uuid]?.get()?.let { skin -> it.skin = skin }
            // That replaced the cape too (the cape mixin ran during extract), put it back.
            PlayerCustomization.applyCape(it, req.target.uuid)
        }

        val pitchRad = Math.toRadians(req.pitch.toDouble()).toFloat()
        // rotateZ(PI) stands the model up, X tilt is the view elevation.
        val pose = Quaternionf().rotateZ(Math.PI.toFloat()).rotateX(pitchRad)
        val camera = Quaternionf().rotateX(pitchRad)

        // The same lookup the mixin makes, so framing can't disagree.
        val size = Platform.cosmetics.scaleFor(player.uuid) ?: Scale.NONE
        val boxW = (req.x1 - req.x0).toFloat()
        val boxH = (req.y1 - req.y0).toFloat()

        // Center the model (spans MODEL_HEIGHT * |scaleY| up, mirrored or not). Focused previews zoom on the cosmetic.
        val translation = Vector3f(0f, (focus?.height ?: (MODEL_HEIGHT * 0.5f)) * abs(size.y), 0f)
        val scissor = ScreenRectangle(req.x0, req.y0, req.x1 - req.x0, req.y1 - req.y0)

        val baseFill = if (req.target != null) AVATAR_BASE_FILL else EDITOR_BASE_FILL
        val fitFill = if (req.target != null) AVATAR_FIT_FILL else EDITOR_FIT_FILL
        val scale = frameScale(boxW, boxH, size, baseFill, fitFill) * (focus?.zoom ?: 1f)

        val state = GuiEntityRenderState(rs, translation, pose, camera, req.x0, req.y0, req.x1, req.y1, scale, scissor)
        // Skija, not a PiP blit, so it sits over the panel. The caller key ties this frame's capture to the next draw.
        SkijaEntity.draw(key, state, req.x0, req.y0, req.x1 - req.x0, req.y1 - req.y0)
    }

    private fun dress(player: LivingEntity, gear: PreviewGear?) {
        fun stack(raw: String?): ItemStack = raw?.let(ItemStacks::forSpec) ?: ItemStack.EMPTY
        player.setItemSlot(EquipmentSlot.HEAD, stack(gear?.helmet))
        player.setItemSlot(EquipmentSlot.CHEST, stack(gear?.chestplate))
        player.setItemSlot(EquipmentSlot.LEGS, stack(gear?.leggings))
        player.setItemSlot(EquipmentSlot.FEET, stack(gear?.boots))
        player.setItemSlot(EquipmentSlot.MAINHAND, stack(gear?.mainHand))
    }

    /** The local player for the editor, otherwise a cached fake player. Null without a world (main menu). */
    private fun playerFor(target: Target?): LivingEntity? {
        if (target == null) return Minecraft.getInstance().player
        val level = Minecraft.getInstance().level ?: return null
        return fakePlayers.getOrPut(target.uuid) {
            // Mutable map, because the 2-arg GameProfile uses the shared immutable EMPTY map.
            val properties = LinkedHashMultimap.create<String, Property>()
            // Signature makes it secure if present, unsigned still works (requireSecure = false).
            target.textures?.let { properties.put("textures", Property("textures", it, target.signature)) }
            val profile = GameProfile(target.uuid, target.name, PropertyMap(properties))
            val fake = RemotePlayer(level, profile)
            // render reads getId() (item-model seed), which throws if unset. Not in the world's id index, so no clash.
            fake.id = PREVIEW_ENTITY_ID
            // requireSecure = false so unsigned properties still render. Downloads async, the supplier fills in.
            fakeSkins[target.uuid] = Minecraft.getInstance().skinManager.createLookup(profile, false)
            fake
        }
    }

    /** Render state, body at [yaw] (0 = facing the viewer), restoring live rotations. partialTick 1.0 skips lerp. */
    private fun extractAtYaw(player: LivingEntity, yaw: Float): EntityRenderState {
        val sBody = player.yBodyRot; val sHead = player.yHeadRot
        val sY = player.yRot; val sX = player.xRot
        val look = 180f + yaw
        player.yBodyRot = look; player.yHeadRot = look; player.yRot = look; player.xRot = 0f
        val rs = Minecraft.getInstance().entityRenderDispatcher.extractEntity(player, 1.0f)
        player.yBodyRot = sBody; player.yHeadRot = sHead; player.yRot = sY; player.xRot = sX
        rs.shadowRadius = 0f // a shadow gets clipped to a half-disc
        rs.outlineColor = 0
        // Full-bright like the inventory portrait, the fake player sits unlit at the origin.
        rs.lightCoords = FULL_BRIGHT
        // Otherwise BlackVertex distance-culls the origin-placed fake player (32 blocks).
        rs.distanceToCameraSq = 0.0
        return rs
    }

    /** Skin from a bundled texture (`assets/<namespace>/<path>`): no download, ready on the first frame. Cached. */
    private fun bundledSkin(path: String): PlayerSkin? = bundledSkins.getOrPut(path) {
        val trimmed = path.removePrefix("assets/")
        val namespace = trimmed.substringBefore('/', "")
        val file = trimmed.substringAfter('/', "")
        if (namespace.isEmpty() || file.isEmpty()) return null
        // Both fields: the 1-arg ctor derives `textures/<id>.png`, doubling the path and silently using the default.
        val id = Identifier.fromNamespaceAndPath(namespace, file)
        PlayerSkin(ClientAsset.ResourceTexture(id, id), null, null, PlayerModelType.WIDE, false)
    }

    /** Where the worn cosmetic sits, from its attachment preset and Y offset. Null if unknown: frame the whole body. */
    private fun focusOf(uuid: UUID): Focus? {
        val item = Platform.cosmetics.itemsFor(uuid).firstOrNull() ?: return null
        val preset = runCatching { AttachmentPoint.valueOf(item.attach.point) }.getOrNull() ?: return null
        // Per attachment point, each with its own aim. Back-worn pieces start turned around.
        val focus = when (preset) {
            AttachmentPoint.HEAD_TOP -> Focus(1.78f, 2.6f)
            AttachmentPoint.LOWER_BACK -> Focus(1.25f, 2.2f, yaw = 180f)
            AttachmentPoint.UPPER_BACK -> Focus(1.45f, 2.0f, yaw = 180f)
            AttachmentPoint.WAIST -> Focus(1.05f, 2.0f)
            AttachmentPoint.LEFT_WRIST, AttachmentPoint.RIGHT_WRIST -> Focus(1.05f, 2.2f)
            AttachmentPoint.LEFT_ANKLE, AttachmentPoint.RIGHT_ANKLE -> Focus(0.45f, 2.2f)
        }
        // That space points +Y down.
        return focus.copy(height = focus.height - item.attach.offsetY)
    }

    /**
     * GUI zoom for [size] in the box. Vanilla size fills [baseFill] of the height. Larger models zoom out to fit
     * [fitFill], smaller ones don't get blown up. Uses the diagonal footprint so spinning doesn't pulse the zoom.
     */
    private fun frameScale(boxW: Float, boxH: Float, size: Scale, baseFill: Float, fitFill: Float): Float {
        val base = boxH * baseFill / MODEL_HEIGHT
        // A flattened axis would fit at any zoom.
        val vertical = (MODEL_HEIGHT * abs(size.y)).coerceAtLeast(MIN_EXTENT)
        val horizontal = hypot(MODEL_WIDTH * abs(size.x), MODEL_DEPTH * abs(size.z)).coerceAtLeast(MIN_EXTENT)
        val fit = min(boxH * fitFill / vertical, boxW * fitFill / horizontal)
        return min(base, fit)
    }

    /** Only read as an item-model seed. */
    private const val PREVIEW_ENTITY_ID = Int.MAX_VALUE

    private val FRESH = 150.milliseconds

    // Model size in blocks (32x16x8 px). Height is the 2-block model, not the 1.8 hitbox. Shared with the scale mixin.
    private const val MODEL_HEIGHT = 2.0f
    private const val MODEL_WIDTH = 1.0f
    private const val MODEL_DEPTH = 0.5f

    /** A flattened ("pancake") axis. */
    private const val MIN_EXTENT = 0.01f

    // One fill for both, so every avatar reads at the same scale.
    private const val AVATAR_BASE_FILL = 0.9f
    private const val AVATAR_FIT_FILL = 0.9f

    // Half the box leaves headroom, so the height slider visibly grows the model until near the |2| cap.
    private const val EDITOR_BASE_FILL = 0.5f
    private const val EDITOR_FIT_FILL = 0.95f

    /** Sky and block light maxed. */
    private const val FULL_BRIGHT = 0xF000F0
}
