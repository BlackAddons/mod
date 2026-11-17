package org.blackaddons.platform.compat

import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import org.blackaddons.api.util.concurrent.OffThread
import org.lwjgl.glfw.GLFWNativeWin32
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.net.URI
import java.nio.file.Path

object NativeDesktop {

    fun openUri(uri: URI) = Util.getPlatform().openUri(uri)

    fun openUri(url: String) = Util.getPlatform().openUri(url)

    fun openPath(path: Path) = Util.getPlatform().openPath(path)

    fun win32Window(): Long = GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().window.handle())

    fun chooseFile(title: String, extensions: Set<String>, onResult: (Path?) -> Unit) {
        OffThread.run(
            name = "file-picker",
            work = {
                MemoryStack.stackPush().use { stack ->
                    val filters = stack.mallocPointer(extensions.size)
                    extensions.forEach { filters.put(stack.UTF8("*.$it")) }
                    filters.flip()
                    TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "Files", false)?.let(Path::of)
                }
            },
            onFailure = { null },
            onResult = onResult,
        )
    }
}
