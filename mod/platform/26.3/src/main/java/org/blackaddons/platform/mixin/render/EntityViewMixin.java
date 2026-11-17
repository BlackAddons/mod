package org.blackaddons.platform.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.blackaddons.platform.impl.world.EntityView;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/*
 * Runs after the main frame's outline and post passes, so overwritten level state isn't read again until the next
 * extract. The extractor only re-culls past a camera step, so each view applies its own frustum and the main one gets
 * restored after. LevelExtractorMixin/LevelRendererMixin still hold camera-following state.
 */
@Mixin(GameRenderer.class)
public abstract class EntityViewMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private Camera mainCamera;
    @Shadow @Final @Mutable private RenderTarget mainRenderTarget;
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow @Final @Mutable private ProjectionMatrixBuffer levelProjectionMatrixBuffer;

    @Shadow protected abstract void extractCamera(DeltaTracker deltaTracker, float worldPartialTicks);

    @Shadow public abstract void renderLevel();

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void blackaddons$renderEntityViews(CallbackInfo ci) {
        if (minecraft.level == null || minecraft.player == null) return;
        List<EntityView.View> views = EntityView.due();
        if (views.isEmpty()) return;
        RenderTarget main = mainRenderTarget;
        ProjectionMatrixBuffer mainProjection = levelProjectionMatrixBuffer;
        Entity cameraEntity = mainCamera.entity();
        DeltaTracker deltaTracker = minecraft.getDeltaTracker();
        LevelExtractorFrustumInvoker culling = (LevelExtractorFrustumInvoker) minecraft.levelExtractor;
        float worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        GpuBuffer mainGlobals = RenderSystem.getGlobalSettingsUniform();
        try {
            for (EntityView.View view : views) {
                EntityView.begin(view);
                mainCamera.setEntity(view.getEntity());
                mainCamera.update(deltaTracker);
                culling.blackaddons$applyFrustum(mainCamera.getCullFrustum());
                extractCamera(deltaTracker, worldPartialTicks);
                minecraft.levelExtractor.extract(deltaTracker, mainCamera, worldPartialTicks);
                blackaddons$skipRingBufferPasses(gameRenderState.levelRenderState);
                RenderTarget target = view.getTarget();
                mainRenderTarget = target;
                levelProjectionMatrixBuffer = view.getProjection();
                RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(0.0F, 0.0F, 0.0F, 1.0F), target.getDepthTexture(), 0.0D);
                blackaddons$uploadGlobals(view.getGlobals(), mainCamera.position());
                renderLevel();
                mainRenderTarget = main;
                levelProjectionMatrixBuffer = mainProjection;
            }
        } finally {
            mainRenderTarget = main;
            levelProjectionMatrixBuffer = mainProjection;
            EntityView.end();
            mainCamera.setEntity(cameraEntity);
            mainCamera.update(deltaTracker);
            culling.blackaddons$applyFrustum(mainCamera.getCullFrustum());
            if (mainGlobals != null) RenderSystem.setGlobalSettingsUniform(mainGlobals);
        }
    }

    /*
     * Clouds and the entity outline chain write ring buffers sized for one level render per frame. With several views
     * in one submit they wrap onto a slot fenced by this same submit and the GL backend throws.
     */
    @Unique
    private static void blackaddons$skipRingBufferPasses(LevelRenderState level) {
        level.cloudColor = 0;
        level.shouldShowEntityOutlines = false;
    }

    /* Terrain is placed relative to the globals' camera position, uploaded once per frame for the main camera. */
    @Unique
    private void blackaddons$uploadGlobals(GlobalSettingsUniform globals, Vec3 cameraPos) {
        globals.update(
            gameRenderState.windowRenderState.width, gameRenderState.windowRenderState.height,
            gameRenderState.optionsRenderState.glintStrength, gameRenderState.levelRenderState.gameTime,
            gameRenderState.levelRenderState.worldPartialTicks, gameRenderState.optionsRenderState.menuBackgroundBlurriness,
            cameraPos, gameRenderState.optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS
        );
    }
}
