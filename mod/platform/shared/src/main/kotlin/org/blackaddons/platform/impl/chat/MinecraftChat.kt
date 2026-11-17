package org.blackaddons.platform.impl.chat
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import org.blackaddons.api.chat.ChatHost
import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.ChatRetentions
import org.blackaddons.api.chat.RevisableLine
import org.blackaddons.platform.chat.ChatLines
import org.blackaddons.platform.compat.chatComponent
import org.blackaddons.platform.compat.openScreen
import org.blackaddons.platform.util.onClient
import org.blackaddons.platform.util.readOnClient
import java.awt.Color

/**
 * Every call hops to the client thread: adding a line wraps text, which bakes glyphs through `RenderSystem` and throws
 * off-thread. Callers are often workers. Components get built on the caller's thread.
 */
object MinecraftChat : ChatHost {
    override fun local(text: String) {
        val component = Component.literal(text)
        onClient { it.chatComponent.addClientSystemMessage(component) }
    }

    override fun local(text: String, color: Color) {
        val styled = Component.literal(text).withStyle(
            Style.EMPTY.withColor(TextColor.fromRgb(color.rgb and 0xFFFFFF)),
        )
        onClient { it.chatComponent.addClientSystemMessage(styled) }
    }

    override fun local(message: ChatMessage) {
        val component = message.toComponent()
        onClient { it.chatComponent.addClientSystemMessage(component) }
    }

    override fun revisable(message: ChatMessage): RevisableLine {
        val component = message.toComponent()
        val line = MinecraftChatLine()
        onClient { mc ->
            val chat = mc.chatComponent
            chat.addClientSystemMessage(component)
            // Asked for, not assumed: ChatComponentMixin might store a copy with cosmetic names.
            line.bind((chat as? ChatLines)?.blackaddonsNewestMessage())
        }
        return line
    }

    // The live history, which the client thread changes as lines arrive. Other threads get a copy made there.
    override val lines: List<ChatLine>
        get() = if (Minecraft.getInstance().isSameThread) liveLines() else readOnClient { liveLines().toList() }

    override fun lineAt(x: Double, y: Double): ChatLine? =
        readOnClient { (Minecraft.getInstance().chatComponent as? ChatLines)?.blackaddonsLineAt(x, y) }

    private fun liveLines(): List<ChatLine> = (Minecraft.getInstance().chatComponent as? ChatLines)?.blackaddonsLines().orEmpty()

    fun saveForRestart() {
        if (!ChatRetentions.keepsMessagesOnRestart) return
        val chat = Minecraft.getInstance().chatComponent as? ChatLines ?: return
        ChatRetentions.saveMessages(chat.blackaddonsSavedMessages())
    }

    override fun openInput(text: String) = onClient { it.openScreen(ChatScreen(text, false)) }

    @Volatile private var view: ((ChatLine) -> Boolean)? = null

    fun shows(line: ChatLine): Boolean = view?.invoke(line) ?: true

    override fun showOnly(filter: ((ChatLine) -> Boolean)?) = onClient { mc ->
        view = filter
        (mc.chatComponent as? ChatLines)?.blackaddonsRefreshView()
    }

    override fun send(text: String) = onClient { it.connection?.sendChat(text) }

    private val dispatching = HashSet<String>()

    override fun sendCommand(command: String) = onClient { mc ->
        val connection = mc.connection ?: return@onClient
        val root = command.substringBefore(' ')
        val dispatcher = ClientCommands.getActiveDispatcher()
        if (root !in dispatching && dispatcher?.root?.getChild(root) != null) {
            val source = connection.suggestionsProvider as FabricClientCommandSource
            dispatching += root
            try {
                dispatcher.execute(command, source)
                return@onClient
            } catch (e: CommandSyntaxException) {
                val forwarded = e.type === CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand() ||
                    e.type === CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                if (!forwarded) {
                    source.sendError(Component.literal(e.message.orEmpty()))
                    return@onClient
                }
            } finally {
                dispatching -= root
            }
        }
        connection.send(ServerboundChatCommandPacket(command))
    }

    /** Handle to one line, bound once the client thread prints it. Revisions queue behind the print, nothing waits. */
    private class MinecraftChatLine : RevisableLine {

        @Volatile private var held: GuiMessage? = null

        fun bind(added: GuiMessage?) {
            held = added
        }

        override fun revise(message: ChatMessage) {
            val component = message.toComponent()
            onClient { mc ->
                val original = held ?: return@onClient
                val chat = mc.chatComponent as? ChatLines ?: return@onClient
                // Null once it aged out of history.
                chat.blackaddonsReplaceMessage(original, component)?.let { held = it }
            }
        }
    }
}
