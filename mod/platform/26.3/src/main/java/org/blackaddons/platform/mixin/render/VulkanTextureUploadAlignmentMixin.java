package org.blackaddons.platform.mixin.render;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/*
 * Uploads share one staging buffer at alignment 1, so an odd-sized grayscale glyph leaves the next RGBA copy at an
 * offset Vulkan forbids (bufferOffset has to be a multiple of the texel size). The driver can fault the device on it.
 */
@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanTextureUploadAlignmentMixin {
    @Unique
    private static final long BLACKADDONS$TEXEL_ALIGNMENT = 4L;

    @ModifyArg(
        method = "writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanTransientMemory;uploadStaging(Ljava/nio/ByteBuffer;JI)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
        ),
        index = 1
    )
    private long blackaddons$alignToTexel(long alignment) {
        return Math.max(alignment, BLACKADDONS$TEXEL_ALIGNMENT);
    }
}
