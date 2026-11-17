package org.blackaddons.platform.impl

import net.minecraft.client.Minecraft
import org.blackaddons.api.host.DesktopHost
import org.blackaddons.core.log.Log
import org.blackaddons.platform.compat.NativeDesktop
import org.blackaddons.platform.compat.setWindowIcon
import org.blackaddons.platform.impl.MinecraftDesktop.openUrl
import org.blackaddons.platform.util.onClient
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opens links and folders through Minecraft's own opener, so it behaves like chat links and the resource pack folder
 * button on every desktop. There's no confirmation screen here, so [openUrl] checks the scheme. Callers only pass
 * mod-built URLs.
 */
internal object MinecraftDesktop : DesktopHost {

    override fun openUrl(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            Log.main.warn("Refusing to open a link that is not http(s): {}", url)
            return
        }
        runCatching { NativeDesktop.openUri(URI.create(url)) }
            .onFailure { Log.main.warn("Could not open {}: {}", url, it.toString()) }
    }

    override fun openPath(path: Path) {
        runCatching {
            // A missing folder looks like a dead button. Only if missing: createDirectories throws on an existing file.
            if (Files.notExists(path)) Files.createDirectories(path)
            NativeDesktop.openPath(path)
        }.onFailure { Log.main.warn("Could not open {}: {}", path, it.toString()) }
    }

    override fun chooseFile(title: String, extensions: Set<String>, onSelected: (Path) -> Unit) {
        require(extensions.isNotEmpty()) { "A file picker needs at least one extension" }
        NativeDesktop.chooseFile(title, extensions) { selected ->
            val path = selected?.toAbsolutePath()?.normalize() ?: return@chooseFile
            Minecraft.getInstance().execute { onSelected(path) }
        }
    }

    @Volatile
    @JvmStatic
    var titleOverride: String? = null
        private set

    override fun setWindowTitle(title: String?) {
        if (title == titleOverride) return
        titleOverride = title
        onClient { it.updateTitle() }
    }

    override fun setWindowIcon(pngBySize: Map<Int, ByteArray>?) {
        val files = pngBySize?.let(WindowIcons::files)
        val mc = Minecraft.getInstance()
        /*
         * schedule, not execute: at startup this runs inside Minecraft's constructor, before the game sets its own
         * icon.
         */
        mc.schedule {
            runCatching { mc.setWindowIcon(files) }
                .onFailure { Log.main.warn("Could not set the window icon: {}", it.toString()) }
        }
    }
}
