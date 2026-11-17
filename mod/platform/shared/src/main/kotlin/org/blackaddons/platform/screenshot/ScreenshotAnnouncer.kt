package org.blackaddons.platform.screenshot

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.world.ScreenshotEvent
import java.io.File
import java.util.function.Consumer

/**
 * Turns the "screenshot saved" callback into a [ScreenshotEvent]. Only that chat line knows the file (as its click
 * target), so we read the path from it and post the event on the client thread. The line passes on unless cancelled.
 * Other shapes pass through.
 */
internal object ScreenshotAnnouncer {

    @JvmStatic
    fun wrap(callback: Consumer<Component>): Consumer<Component> = Consumer { message ->
        val file = savedFile(message)
        // Panoramas are a debug tool, not six screenshots.
        if (file == null || file.name.startsWith(PANORAMA_PREFIX)) {
            callback.accept(message)
            return@Consumer
        }
        // Saved on the IO pool, listeners get the client thread.
        Minecraft.getInstance().execute {
            val event = EventBus.post(ScreenshotEvent(file.toPath()))
            if (!event.cancelled) callback.accept(message)
        }
    }

    private fun savedFile(message: Component): File? {
        val contents = message.contents as? TranslatableContents ?: return null
        if (contents.key != SUCCESS_KEY) return null
        val name = contents.args.firstOrNull() as? Component ?: return null
        return (name.style.clickEvent as? ClickEvent.OpenFile)?.file()
    }

    private const val SUCCESS_KEY = "screenshot.success"
    private const val PANORAMA_PREFIX = "panorama_"
}
