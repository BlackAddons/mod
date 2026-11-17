package org.blackaddons.platform.mixin.command;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import org.blackaddons.platform.util.IdentityFirstMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

@Mixin(value = ClientCommandInternals.class, remap = false)
public abstract class ClientCommandInternalsMixin {

    @ModifyVariable(method = "addCommands", at = @At("STORE"), ordinal = 0)
    private static Map<CommandNode<FabricClientCommandSource>, CommandNode<FabricClientCommandSource>> blackaddons$identityNodeMap(
            Map<CommandNode<FabricClientCommandSource>, CommandNode<FabricClientCommandSource>> original
    ) {
        return new IdentityFirstMap<>();
    }
}
