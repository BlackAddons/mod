package org.blackaddons.platform.compat

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Overlay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.CustomFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector4f

/* The McCompat entries every supported version spells the same way. Version-specific ones stay in McCompat. */

/** 26.2 groups colored blocks into color collections. */
internal fun BlockState.isBlueTerracotta(): Boolean = `is`(Blocks.DYED_TERRACOTTA.blue())

internal fun BlockState.isBloodDoorWool(): Boolean = block in bloodDoorWoolBlocks

private val bloodDoorWoolBlocks = setOf(
    Blocks.WOOL.gray(),
    Blocks.WOOL.red(),
    Blocks.WOOL.black(),
    Blocks.WOOL.lightGray(),
    Blocks.WOOL.green(),
    Blocks.WOOL.orange(),
)

internal val Minecraft.currentScreen: Screen? get() = gui.screen()

internal val Minecraft.currentOverlay: Overlay? get() = gui.overlay()

internal val Minecraft.hudHidden: Boolean get() = gui.hud.isHidden

/** `setScreenAndShow` didn't move. */
internal fun Minecraft.openScreen(target: Screen?) = gui.setScreen(target)

internal val Minecraft.chatComponent: ChatComponent get() = gui.hud.chat

internal val Minecraft.mainCamera: Camera get() = gameRenderer.mainCamera()

/** Like F3+A: invalidating only the renderer drops meshes without marking sections dirty, so nothing rebuilds. */
internal fun Minecraft.redrawWorld() {
    levelExtractor.allChanged()
}

/**
 * 26.2 renders custom submissions before translucent terrain, so water would blend over waypoint fills. afterTerrain
 * runs after water with normal depth testing.
 */

/** Hooks [onFrame] to the stage this version can draw from. */
internal fun registerWorldPass(onFrame: (LevelRenderContext) -> Unit) {
    LevelRenderEvents.COLLECT_SUBMITS.register { ctx -> onFrame(ctx) }
}

internal inline fun LevelRenderContext.drawGeometry(
    rt: RenderType,
    stack: PoseStack,
    crossinline emit: (PoseStack.Pose, VertexConsumer) -> Unit,
) {
    val collector = submitNodeCollector()
    val renderer = SubmitNodeCollector.CustomGeometryRenderer { pose, buf -> emit(pose, buf) }
    val afterTerrain = collector.afterTerrain()
    if (afterTerrain == null) {
        collector.submitCustomGeometry(stack, rt, renderer)
    } else {
        afterTerrain.submit(CustomFeatureRenderer.Submit(stack.last().copy(), rt, renderer))
    }
}

/** The level renderer drains the submit collector. */
@Suppress("UnusedReceiverParameter")
internal fun LevelRenderContext.endWorldPass() = Unit

internal fun SubmitNodeCollector.afterTerrain() =
    (this as? SubmitNodeStorage)?.order(0)?.afterTerrain

internal fun Minecraft.showTitleCard(
    title: Component,
    subtitle: Component,
    fadeIn: Int,
    stay: Int,
    fadeOut: Int,
) = with(gui.hud) {
    setTimes(fadeIn, stay, fadeOut)
    setTitle(title)
    setSubtitle(subtitle)
}

/* Vanilla's see-through grayscale text sorts by distance without depth, so a shadow can land on its glyph. */
private val emitOrderGrayscaleTextSeeThrough = Util.memoize<Identifier, RenderType> { texture ->
    RenderType.create(
        "ba_text_grayscale_see_through",
        RenderSetup.builder(RenderPipelines.TEXT_GRAYSCALE_SEE_THROUGH).withTexture("Sampler0", texture).useLightmap().createRenderSetup(),
    )
}

internal fun emitOrderSeeThroughText(texture: Identifier, colored: Boolean, vanilla: RenderType): RenderType =
    if (colored) vanilla else emitOrderGrayscaleTextSeeThrough.apply(texture)

internal fun Minecraft.joinSessionServer(serverId: String) =
    services().sessionService().joinServer(user.profileId, user.accessToken, serverId)

internal fun clearFontPage(encoder: CommandEncoder, texture: GpuTexture) =
    encoder.clearColorTexture(texture, Vector4f(0f, 0f, 0f, 0f))

internal fun uploadFontMask(
    encoder: CommandEncoder,
    texture: GpuTexture,
    image: NativeImage,
    x: Int,
    y: Int,
) = encoder.writeToTexture(texture, image, 0, 0, x, y)
