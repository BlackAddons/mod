package org.blackaddons.platform.compat

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.feature.TextFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackResources
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Util
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import org.blackaddons.platform.impl.WindowIcons
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW

/**
 * Where versions disagree on where something lives, so mod/platform/shared stays one source. A platform missing one
 * won't compile. API-shape differences only, behavioral ones get their own file.
 */

internal fun Minecraft.releaseCursorAt(x: Double, y: Double) =
    InputConstants.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_NORMAL, x, y)

internal fun Minecraft.textInputFocusChanged(owner: Any, focused: Boolean) =
    textInputManager().onTextInputFocusChange(focused)

internal fun newViewTarget(label: String, width: Int, height: Int): RenderTarget =
    TextureTarget(label, width, height, true, GpuFormat.RGBA8_UNORM)

internal fun LevelRenderContext.drawText(
    stack: PoseStack, font: Font, text: FormattedCharSequence, x: Float,
    displayMode: Font.DisplayMode, light: Int, color: Int,
) {
    val collector = submitNodeCollector()
    val afterTerrain = collector.afterTerrain()
    if (afterTerrain == null) {
        collector.submitText(stack, x, 0f, text, false, displayMode, light, color, 0, 0)
    } else {
        afterTerrain.submit(TextFeatureRenderer.Submit(
            Matrix4f(stack.last().pose()), x, 0f, text, false, displayMode, light, color, 0, 0,
        ))
    }
}

private val emitOrderTextPolygonOffset = Util.memoize<Identifier, RenderType> { texture ->
    RenderType.create(
        "ba_text_polygon_offset",
        RenderSetup.builder(RenderPipelines.TEXT_POLYGON_OFFSET).withTexture("Sampler0", texture).useLightmap().createRenderSetup(),
    )
}

private val emitOrderGrayscaleTextPolygonOffset = Util.memoize<Identifier, RenderType> { texture ->
    RenderType.create(
        "ba_text_grayscale_polygon_offset",
        RenderSetup.builder(RenderPipelines.TEXT_GRAYSCALE_POLYGON_OFFSET).withTexture("Sampler0", texture).useLightmap().createRenderSetup(),
    )
}

internal fun emitOrderPolygonOffsetText(texture: Identifier, colored: Boolean): RenderType =
    (if (colored) emitOrderTextPolygonOffset else emitOrderGrayscaleTextPolygonOffset).apply(texture)

internal fun GuiGraphicsExtractor.drawTooltip(font: Font, lines: List<ClientTooltipComponent>, x: Int, y: Int) =
    tooltip(font, lines, x, y, DefaultTooltipPositioner.INSTANCE, null)

internal fun Player.swingLikeStartUseItem(result: InteractionResult) {
    if ((result as? InteractionResult.Success)?.swingSource() == InteractionResult.SwingSource.CLIENT) {
        swing(InteractionHand.MAIN_HAND)
    }
}

internal fun blockMaterial(sprite: TextureAtlasSprite, layer: ChunkSectionLayer) =
    BakedQuad.MaterialInfo(sprite, layer, Sheets.cutoutBlockItemSheet(), -1, true, 0)

internal fun BakedQuad.MaterialInfo.withSprite(sprite: TextureAtlasSprite, replacement: BakedQuad.MaterialInfo?) =
    BakedQuad.MaterialInfo(
        sprite,
        replacement?.layer() ?: layer(),
        replacement?.itemRenderType() ?: itemRenderType(),
        -1,
        shade(),
        replacement?.lightEmission() ?: lightEmission(),
    )

internal fun ClientboundPlayerChatPacket.unsignedContentOrNull(): Component? = unsignedContent()

/** 26.3 changed the pack type that window icons are read through. */
internal fun Minecraft.setWindowIcon(files: Map<String, ByteArray>?) {
    val vanilla: PackResources = vanillaPackResources
    val pack = files?.let { WindowIcons.overlay(vanilla, PackResources::class.java, it) } ?: vanilla
    window.setIcon(pack, WindowIcons.iconSet(custom = files != null))
}

internal fun Minecraft.displayRefreshRate(): Int? = window.refreshRate.takeIf { it > 0 }
