package org.blackaddons.platform.impl.gui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.blackaddons.api.gui.ScreenHost
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.compat.openScreen
import org.blackaddons.platform.compat.textInputFocusChanged
import org.blackaddons.platform.ui.screen.ScreenOverlay
import org.blackaddons.platform.ui.screen.SkijaHostScreen
import org.blackaddons.platform.util.onClient

/** [ScreenHost]. Mutators hop to the client thread, because `setScreen` changes render state. */
object MinecraftScreen : ScreenHost {

    // Plain field reads answer on the caller's thread.
    override val anyScreenOpen: Boolean get() = Minecraft.getInstance().currentScreen != null

    override val chatOpen: Boolean get() = Minecraft.getInstance().currentScreen is ChatScreen

    override fun show() = onClient { mc ->
        ScreenOverlay.uncover()
        if (mc.currentScreen !is SkijaHostScreen) mc.setScreenAndShow(SkijaHostScreen())
    }

    override fun showOver() = onClient { mc ->
        val screen = mc.currentScreen
        if (screen == null || screen is SkijaHostScreen) show() else ScreenOverlay.cover(screen)
    }

    override fun hide() = onClient { mc ->
        ScreenOverlay.uncover()
        if (mc.currentScreen is SkijaHostScreen) mc.openScreen(null)
    }

    override fun onClientThread(block: () -> Unit) = onClient { block() }

    override val isClientThread: Boolean get() = Minecraft.getInstance().isSameThread

    private var textOwner: Any? = null

    override fun textInputFocusChanged(owner: Any, focused: Boolean) = onClient { mc ->
        if (focused) textOwner = owner else if (textOwner === owner) textOwner = null
        mc.textInputFocusChanged(owner, focused)
    }

    fun resumeTextInput() = onClient { mc ->
        textOwner?.let { mc.textInputFocusChanged(it, true) }
    }
}
