package org.blackaddons.platform.impl.command
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

/** Fabric's `ClientCommands` (renamed from `ClientCommandManager` in 26.1). */
private object ClientCommandsFactory : ClientCommandFactory {
    override fun literal(name: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(name)

    override fun argument(name: String, type: ArgumentType<*>): RequiredArgumentBuilder<FabricClientCommandSource, *> =
        ClientCommands.argument(name, type)
}

object MinecraftCommands : AbstractMinecraftCommands(ClientCommandsFactory)
