package org.blackaddons.platform.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.blackaddons.core.features.impl.visual.EntityQol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {

    @Inject(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blackaddons$hideArmor(EquipmentClientInfo.LayerType layer, ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier playerTexture, int outlineColor, int order, CallbackInfo ci) {
        if (!EntityQol.INSTANCE.enabled || !EntityQol.INSTANCE.getHideArmor().getValue()) return;

        switch (layer) {
            case HUMANOID, HUMANOID_LEGGINGS, HUMANOID_BABY, WOLF_BODY, HORSE_BODY, NAUTILUS_BODY -> ci.cancel();
            default -> { }
        }
    }
}
