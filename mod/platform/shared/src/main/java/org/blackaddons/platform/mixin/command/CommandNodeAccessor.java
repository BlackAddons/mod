package org.blackaddons.platform.mixin.command;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CommandNode.class, remap = false)
public interface CommandNodeAccessor<S> {
    @Accessor("children")
    Map<String, CommandNode<S>> blackaddons$getChildrenMap();

    @Accessor("literals")
    Map<String, LiteralCommandNode<S>> blackaddons$getLiteralsMap();

    @Accessor("arguments")
    Map<String, ArgumentCommandNode<S, ?>> blackaddons$getArgumentsMap();
}
