package org.blackaddons.platform.compat

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.GpuFormat
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.feature.TextFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackMetadataResources
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Util
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import org.blackaddons.platform.impl.WindowIcons
import org.joml.Matrix4f
import kotlin.math.roundToInt

/**
 * Where versions disagree on where something lives, so mod/platform/shared stays one source. A platform missing one
 * won't compile. API-shape differences only, behavioral ones get their own file.
 */

internal fun Minecraft.releaseCursorAt(x: Double, y: Double) = InputConstants.releaseMouse(window, x, y)

internal fun Minecraft.textInputFocusChanged(owner: Any, focused: Boolean) =
    textInputManager().onTextInputFocusChange(owner, focused)

internal fun newViewTarget(label: String, width: Int, height: Int): RenderTarget =
    TextureTarget(label, width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT)

internal fun LevelRenderContext.drawText(
    stack: PoseStack, font: Font, text: FormattedCharSequence, x: Float,
    displayMode: Font.DisplayMode, light: Int, color: Int,
) {
    val collector = submitNodeCollector()
    val afterTerrain = collector.afterTerrain()
    /* Improved Transparency folds afterTerrain into OIT, and see-through text types have no OIT pipelines. */
    val oitSeeThrough = displayMode == Font.DisplayMode.SEE_THROUGH && afterTerrain === collector.oitTranslucent()
    if (afterTerrain == null || oitSeeThrough) {
        collector.submitText(stack, x, 0f, text, false, displayMode, light, color, 0, 0)
    } else {
        afterTerrain.submit(TextFeatureRenderer.Submit(
            Matrix4f(stack.last().pose()), displayMode, light,
            TextFeatureRenderer.Content.Text(x, 0f, text, false, color, 0, 0),
        ))
    }
}

private fun SubmitNodeCollector.oitTranslucent() =
    (this as? SubmitNodeStorage)?.order(0)?.oitTranslucent

private val emitOrderTextPolygonOffset = Util.memoize<Identifier, RenderType> { texture ->
    RenderType.create(
        "ba_text_polygon_offset",
        RenderSetup.builder(RenderPipelines.TEXT_POLYGON_OFFSET).setOitPipelines(RenderPipelines.OIT_TEXT_POLYGON_OFFSET).withTexture("Sampler0", texture).useLightmap().createRenderSetup(),
    )
}

private val emitOrderGrayscaleTextPolygonOffset = Util.memoize<Identifier, RenderType> { texture ->
    RenderType.create(
        "ba_text_grayscale_polygon_offset",
        RenderSetup.builder(RenderPipelines.TEXT_GRAYSCALE_POLYGON_OFFSET).setOitPipelines(RenderPipelines.OIT_TEXT_GRAYSCALE_POLYGON_OFFSET).withTexture("Sampler0", texture).useLightmap().createRenderSetup(),
    )
}

internal fun emitOrderPolygonOffsetText(texture: Identifier, colored: Boolean): RenderType =
    (if (colored) emitOrderTextPolygonOffset else emitOrderGrayscaleTextPolygonOffset).apply(texture)

internal fun GuiGraphicsExtractor.drawTooltip(font: Font, lines: List<ClientTooltipComponent>, x: Int, y: Int) =
    tooltip(font, lines, x, y, DefaultTooltipPositioner.INSTANCE, null, true)

internal fun Player.swingLikeStartUseItem(result: InteractionResult) {
    if ((result as? InteractionResult.Success)?.swingSource() == InteractionResult.SwingSource.PREDICTED) {
        swing(InteractionHand.MAIN_HAND, mainHandItem.interactAnimation, false)
    }
}

internal fun blockMaterial(sprite: TextureAtlasSprite, layer: ChunkSectionLayer) =
    BakedQuad.MaterialInfo(
        sprite, layer,
        Sheets.cutoutBlockItemSheet(), Sheets.cutoutBlockItemGlintSheet(), Sheets.cutoutBlockItemGlintSpecialSheet(),
        -1, null, 0,
    )

internal fun BakedQuad.MaterialInfo.withSprite(sprite: TextureAtlasSprite, replacement: BakedQuad.MaterialInfo?) =
    BakedQuad.MaterialInfo(
        sprite,
        replacement?.layer() ?: layer(),
        replacement?.itemRenderType() ?: itemRenderType(),
        replacement?.itemGlintRenderType() ?: itemGlintRenderType(),
        replacement?.itemGlintSpecialRenderType() ?: itemGlintSpecialRenderType(),
        -1,
        shadeDirectionOverride(),
        replacement?.lightEmission() ?: lightEmission(),
    )

internal fun ClientboundPlayerChatPacket.unsignedContentOrNull(): Component? = unsignedContent().orElse(null)

/** 26.3 reads window icons through `PackMetadataResources`, from the full vanilla pack. */
internal fun Minecraft.setWindowIcon(files: Map<String, ByteArray>?) {
    val vanilla: PackMetadataResources = vanillaPackResources.fullResources()
    val pack = files?.let {
        object : PackMetadataResources by vanilla {
            override fun getRootResource(vararg path: String) =
                WindowIcons.rootResource(it, path) ?: vanilla.getRootResource(*path)
        }
    } ?: vanilla
    window.setIcon(pack, WindowIcons.iconSet(custom = files != null))
}

/* 26.3 dropped Window.getRefreshRate, so we ask the monitor under the window instead. */
internal fun Minecraft.displayRefreshRate(): Int? =
    window.findBestMonitor()?.currentMode()?.refreshRate?.roundToInt()?.takeIf { it > 0 }
