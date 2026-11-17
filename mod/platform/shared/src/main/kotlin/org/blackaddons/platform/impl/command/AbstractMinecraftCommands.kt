package org.blackaddons.platform.impl.command
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.CommandNode
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.network.chat.Component
import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.commands.ArgBuilder
import org.blackaddons.api.commands.ArgType
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.api.commands.CommandContext
import org.blackaddons.api.commands.CommandHost
import org.blackaddons.api.commands.DynamicAlias
import org.blackaddons.api.util.cleanCommand
import org.blackaddons.api.util.splitWhitespace
import org.blackaddons.core.log.Log
import org.blackaddons.platform.impl.chat.toComponent
import org.blackaddons.platform.mixin.command.CommandNodeAccessor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import com.mojang.brigadier.context.CommandContext as BrigadierContext

/** The only per-version difference: `ClientCommandManager` (1.21) vs `ClientCommands` (26.1). */
interface ClientCommandFactory {
    fun literal(name: String): LiteralArgumentBuilder<FabricClientCommandSource>
    fun argument(name: String, type: ArgumentType<*>): RequiredArgumentBuilder<FabricClientCommandSource, *>
}

/**
 * [CommandHost] over `ClientCommandRegistrationCallback`. Registrations get stored and replayed on every registration
 * phase (world join), so late commands show up on the next join. Also keeps dynamic aliases (Shortcut Actions) in the
 * dispatcher for highlighting and tab completion.
 */
abstract class AbstractMinecraftCommands(
    private val commands: ClientCommandFactory,
) : CommandHost {

    private data class Pending(val name: String, val build: CommandBuilder.() -> Unit)
    // Registered from any thread, walked by the callback.
    private val pending = CopyOnWriteArrayList<Pending>()
    private val dynamicAliases = mutableListOf<DynamicAlias>()
    private val registeredAliasNames = mutableSetOf<String>()
    private val displacedAliasNodes = mutableMapOf<String, CommandNode<ClientSuggestionProvider>>()
    private var aliasRoot: CommandNode<ClientSuggestionProvider>? = null
    private val aliasLock = Any()

    init {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            for ((name, build) in pending) {
                // One malformed tree shouldn't block later commands.
                runCatching {
                    val root = commands.literal(name)
                    BrigadierCommandBuilder(commands, root).apply(build)
                    dispatcher.register(root)
                }.onFailure { Log.main.error("Failed to register command '/{}'", name, it) }
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            reapplyDynamicAliases()
        }
    }

    override fun register(name: String, vararg aliases: String, build: CommandBuilder.() -> Unit) {
        pending += Pending(name, build)
        // Rebuilt per alias, because Brigadier root redirects misbehave with argument-less executes.
        for (alias in aliases) pending += Pending(alias, build)
    }

    override fun syncDynamicAliases(aliases: List<DynamicAlias>) {
        synchronized(aliasLock) {
            dynamicAliases.clear()
            dynamicAliases.addAll(aliases)
        }
        Minecraft.getInstance().execute {
            reapplyDynamicAliases()
        }
    }

    fun reapplyDynamicAliases() {
        val connection = Minecraft.getInstance().connection ?: return
        val chatDispatcher = connection.commands
        val root = chatDispatcher.root

        if (root !== aliasRoot) {
            aliasRoot = root
            registeredAliasNames.clear()
            displacedAliasNodes.clear()
        }

        val currentAliases: List<DynamicAlias>
        synchronized(aliasLock) {
            currentAliases = dynamicAliases.toList()
        }

        val currentNames = currentAliases.map { it.name.cleanCommand() }.filter { it.isNotEmpty() }.toSet()

        for (oldName in registeredAliasNames) {
            if (oldName !in currentNames) {
                removeCommandNode(root, oldName)
                displacedAliasNodes.remove(oldName)?.let(root::addChild)
            }
        }
        registeredAliasNames.retainAll(currentNames)

        for (alias in currentAliases.distinctBy { it.name.cleanCommand() }) {
            val name = alias.name.cleanCommand()
            if (name.isEmpty()) continue

            if (name !in registeredAliasNames) {
                root.getChild(name)?.let { displacedAliasNodes[name] = it }
            }
            removeCommandNode(root, name)

            val cleanedTarget = alias.targetCommand.cleanCommand()
            val literal = LiteralArgumentBuilder.literal<ClientSuggestionProvider>(name)
                .executes { 1 }

            if (cleanedTarget.isNotEmpty()) {
                val targetNode = findTargetNode(root, cleanedTarget)
                if (targetNode != null && targetNode.children.isNotEmpty()) {
                    for (child in targetNode.children) {
                        literal.then(child)
                    }
                } else {
                    literal.then(
                        RequiredArgumentBuilder.argument<ClientSuggestionProvider, String>("args", StringArgumentType.greedyString())
                            .suggests { context, builder ->
                                getAliasSuggestions(cleanedTarget, builder, context.source)
                            }
                            .executes { 1 }
                    )
                }
            }

            chatDispatcher.register(literal)
            registeredAliasNames += name
        }
    }

    private fun findTargetNode(
        root: CommandNode<ClientSuggestionProvider>,
        commandPath: String,
    ): CommandNode<ClientSuggestionProvider>? {
        val tokens = commandPath.trim().removePrefix("/").splitWhitespace()
        if (tokens.isEmpty()) return null

        var current: CommandNode<ClientSuggestionProvider> = root
        for (token in tokens) {
            current = current.getChild(token) ?: return null
        }
        return current
    }

    private fun getAliasSuggestions(
        cleanedCommand: String,
        builder: SuggestionsBuilder,
        source: ClientSuggestionProvider,
    ): CompletableFuture<Suggestions> {
        val connection = Minecraft.getInstance().connection ?: return builder.buildFuture()
        val remaining = builder.remaining
        val virtualPrefix = "$cleanedCommand "
        val virtualCommand = "$virtualPrefix$remaining"
        val dispatcher = connection.commands

        return try {
            val parseResults = dispatcher.parse(virtualCommand, source)
            dispatcher.getCompletionSuggestions(parseResults, virtualCommand.length).thenApply { suggestions ->
                val virtualPrefixLen = virtualPrefix.length
                for (suggestion in suggestions.list) {
                    val startInVirtual = suggestion.range.start
                    if (startInVirtual >= virtualPrefixLen) {
                        val relStart = startInVirtual - virtualPrefixLen
                        val actualStart = builder.start + relStart
                        val childBuilder = builder.createOffset(actualStart)
                        childBuilder.suggest(suggestion.text, suggestion.tooltip)
                        builder.add(childBuilder)
                    }
                }
                builder.build()
            }
        } catch (ignored: Exception) {
            builder.buildFuture()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun removeCommandNode(root: CommandNode<*>, name: String) {
        val accessor = root as CommandNodeAccessor<Any?>
        accessor.`blackaddons$getChildrenMap`().remove(name)
        accessor.`blackaddons$getLiteralsMap`().remove(name)
        accessor.`blackaddons$getArgumentsMap`().remove(name)
    }
}

private open class BrigadierCommandBuilder(
    private val commands: ClientCommandFactory,
    private val node: ArgumentBuilder<FabricClientCommandSource, *>,
) : CommandBuilder {

    /** Lets an optional argument replay the parent's action. */
    var action: (CommandContext.() -> Unit)? = null
        private set

    override fun execute(action: CommandContext.() -> Unit) {
        check(this.action == null) {
            "this command node already has an execute block (an optional argument counts as one)"
        }
        this.action = action
        node.executes { brigadierCtx ->
            BrigadierContextWrapper(brigadierCtx).action()
            1
        }
    }

    override fun subcommand(name: String, build: CommandBuilder.() -> Unit) {
        val child = commands.literal(name)
        BrigadierCommandBuilder(commands, child).apply(build)
        node.then(child)
    }

    override fun arg(name: String, type: ArgType, optional: Boolean, build: ArgBuilder.() -> Unit) {
        val argNode = commands.argument(name, type.toBrigadier())
        val child = BrigadierArgBuilder(commands, argNode)
        if (type == ArgType.PlayerArg) child.suggests { Platform.world.onlinePlayerNames }
        child.apply(build)
        node.then(argNode)
        if (optional) {
            val absent = child.action ?: error("optional argument '$name' has no execute block")
            execute(absent)
        }
    }
}

private class BrigadierArgBuilder(
    commands: ClientCommandFactory,
    private val argNode: RequiredArgumentBuilder<FabricClientCommandSource, *>,
) : BrigadierCommandBuilder(commands, argNode), ArgBuilder {

    override fun suggests(candidates: () -> Iterable<String>) {
        argNode.suggests { _, builder ->
            // Substring, not prefix: people remember names by their middle too.
            val typed = builder.remainingLowerCase
            for (candidate in candidates()) {
                if (candidate.lowercase().contains(typed)) builder.suggest(candidate)
            }
            builder.buildFuture()
        }
    }
}

private fun ArgType.toBrigadier(): ArgumentType<*> = when (this) {
    ArgType.StrArg, ArgType.PlayerArg -> StringArgumentType.word()
    ArgType.GreedyStrArg -> StringArgumentType.greedyString()
    is ArgType.IntArg -> IntegerArgumentType.integer(min, max)
    is ArgType.FloatArg -> FloatArgumentType.floatArg(min, max)
    is ArgType.DoubleArg -> DoubleArgumentType.doubleArg(min, max)
    ArgType.BoolArg -> BoolArgumentType.bool()
}

private class BrigadierContextWrapper(
    private val brigadier: BrigadierContext<FabricClientCommandSource>,
) : CommandContext {
    override fun str(name: String): String = StringArgumentType.getString(brigadier, name)
    override fun int(name: String): Int = IntegerArgumentType.getInteger(brigadier, name)
    override fun float(name: String): Float = FloatArgumentType.getFloat(brigadier, name)
    override fun double(name: String): Double = DoubleArgumentType.getDouble(brigadier, name)
    override fun bool(name: String): Boolean = BoolArgumentType.getBool(brigadier, name)

    override fun strOrNull(name: String): String? = if (parsed(name)) str(name) else null
    override fun intOrNull(name: String): Int? = if (parsed(name)) int(name) else null
    override fun floatOrNull(name: String): Float? = if (parsed(name)) float(name) else null
    override fun doubleOrNull(name: String): Double? = if (parsed(name)) double(name) else null
    override fun boolOrNull(name: String): Boolean? = if (parsed(name)) bool(name) else null

    private fun parsed(name: String) = brigadier.nodes.any { it.node.name == name }

    override fun reply(text: String) {
        brigadier.source.sendFeedback(Component.literal(text))
    }

    override fun reply(message: ChatMessage) {
        brigadier.source.sendFeedback(message.toComponent())
    }
}
