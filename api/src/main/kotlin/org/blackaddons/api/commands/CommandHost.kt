package org.blackaddons.api.commands

import org.blackaddons.api.chat.ChatMessage

/**
 * Client-side slash commands, never sent to the server. Register in `onLoad()` or `init()`; commands
 * registered after world join appear on the next join.
 */
interface CommandHost {
    /** Registers `/[name]`. [aliases] get the same tree, e.g. `register("blackaddons", "b") { ... }`. */
    fun register(name: String, vararg aliases: String, build: CommandBuilder.() -> Unit)

    /**
     * Pushes dynamic aliases (e.g. Shortcut Actions) into the dispatcher for highlighting and tab completion.
     */
    fun syncDynamicAliases(aliases: List<DynamicAlias>)
}

data class DynamicAlias(
    val name: String,
    val targetCommand: String = "",
)

/** One command-tree node. Branches ([subcommand], [arg]) combine, and [execute] fires if the line ends exactly here. */
interface CommandBuilder {
    fun execute(action: CommandContext.() -> Unit)

    fun subcommand(name: String, build: CommandBuilder.() -> Unit)

    /**
     * Typed argument read back by [name]. With [optional], [execute] also fires without it (`...OrNull`
     * getters return null); the parent node must not have its own [execute], or registration throws.
     */
    fun arg(name: String, type: ArgType, optional: Boolean = false, build: ArgBuilder.() -> Unit)
}

fun CommandBuilder.subcommands(vararg names: String, build: CommandBuilder.() -> Unit) {
    names.forEach { subcommand(it, build) }
}

/** A typed-argument node that also owns its tab-completion. */
interface ArgBuilder : CommandBuilder {
    /** Tab-completes from [candidates], re-evaluated per keystroke. Overrides the [ArgType] default. */
    fun suggests(candidates: () -> Iterable<String>)
}

/** Named `...Arg` so star imports don't shadow `kotlin.Int` etc. */
sealed class ArgType {
    object StrArg : ArgType()

    /** Rest of the line, spaces included - last argument only. */
    object GreedyStrArg : ArgType()

    /** Bounds are inclusive. */
    data class IntArg(val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE) : ArgType()

    /** Bounds are inclusive. */
    data class FloatArg(val min: Float = Float.NEGATIVE_INFINITY, val max: Float = Float.POSITIVE_INFINITY) : ArgType()

    /** Bounds are inclusive. */
    data class DoubleArg(val min: Double = Double.NEGATIVE_INFINITY, val max: Double = Double.POSITIVE_INFINITY) : ArgType()

    object BoolArg : ArgType()

    /** Suggests online players but accepts any word. */
    object PlayerArg : ArgType()
}

/** Read args by their registered name; the wrong getter for a type throws. */
interface CommandContext {
    fun str(name: String): String
    fun int(name: String): Int
    fun float(name: String): Float
    fun double(name: String): Double
    fun bool(name: String): Boolean

    /** Like the getters above, but null when an `optional` argument is missing. */
    fun strOrNull(name: String): String?
    fun intOrNull(name: String): Int?
    fun floatOrNull(name: String): Float?
    fun doubleOrNull(name: String): Double?
    fun boolOrNull(name: String): Boolean?

    fun reply(text: String)

    /** Build with [ChatMessage.build] or [ChatMessage.legacy]. */
    fun reply(message: ChatMessage)
}
