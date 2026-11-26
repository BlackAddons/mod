package org.blackaddons.platform.compat

import com.mojang.blaze3d.Blaze3D
import net.minecraft.client.Minecraft
import org.blackaddons.core.log.Log
import org.blackaddons.platform.util.onClient
import org.lwjgl.sdl.*
import org.lwjgl.system.MemoryUtil
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

object NativeDesktop {

    fun openUri(uri: URI) = Blaze3D.openUri(uri)

    fun openUri(url: String) {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            Log.main.error("Couldn't open uri '{}'", url, e)
            return
        }
        openUri(uri)
    }

    fun openPath(path: Path) = Blaze3D.openPath(path)

    private fun window(): Long = Minecraft.getInstance().window.handle()

    fun win32Window(): Long = SDLProperties.SDL_GetPointerProperty(
        SDLVideo.SDL_GetWindowProperties(window()), SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, MemoryUtil.NULL,
    )

    // SDL dialogs are main-thread only.
    @Suppress("UNUSED_PARAMETER")
    fun chooseFile(title: String, extensions: Set<String>, onResult: (Path?) -> Unit) = onClient {
        val name = MemoryUtil.memUTF8("Files")
        val pattern = MemoryUtil.memUTF8(extensions.joinToString(";"))
        val filters = SDL_DialogFileFilter.calloc(1)
        filters.get(0).name(name).pattern(pattern)
        lateinit var callback: SDL_DialogFileCallback
        callback = SDL_DialogFileCallback.create { _, fileList, _ ->
            val first = if (fileList == MemoryUtil.NULL) MemoryUtil.NULL else MemoryUtil.memGetAddress(fileList)
            val selected = if (first == MemoryUtil.NULL) null else Path.of(MemoryUtil.memUTF8(first))
            MemoryUtil.memFree(name)
            MemoryUtil.memFree(pattern)
            filters.free()
            callback.free()
            onResult(selected)
        }
        SDLDialog.SDL_ShowOpenFileDialog(callback, MemoryUtil.NULL, window(), filters, null as CharSequence?, false)
    }
}
