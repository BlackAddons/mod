package org.blackaddons.platform.impl

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import org.blackaddons.api.host.ClipboardHost
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.log.Log
import org.blackaddons.platform.compat.ImageClipboard
import org.blackaddons.platform.compat.NativeDesktop
import org.blackaddons.platform.util.onClient
import org.blackaddons.platform.util.readOnClient
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.extension

/** [ClipboardHost] via Minecraft's keyboard handler, pictures go through the OS. */
object MinecraftClipboard : ClipboardHost {

    // GLFW (26.2) and SDL (26.3) need the main thread, macOS crashes otherwise.
    override fun copy(text: String) = onClient { it.keyboardHandler.clipboard = text }

    override fun paste(): String = readOnClient { Minecraft.getInstance().keyboardHandler.clipboard }

    /**
     * Minecraft's clipboard is text only and AWT is headless, so pictures use Win32 here and [ImageClipboard]
     * elsewhere. Read and converted off the client thread.
     */
    override fun copyImage(path: Path, onDone: (Boolean) -> Unit) {
        val finish: (Boolean) -> Unit = { copied ->
            if (!copied) Log.main.warn("Could not copy {} to the clipboard", path)
            onClient { onDone(copied) }
        }
        if (Util.getPlatform() == Util.OS.WINDOWS) {
            val window = NativeDesktop.win32Window()
            OffThread.run(name = "copy-image", work = { WindowsClipboard.copy(path, window) }, onFailure = { false }, onResult = finish)
            return
        }
        OffThread.run(name = "copy-image", work = { png(path) }, onFailure = { null }) { png ->
            if (png == null) finish(false)
            else onClient {
                try {
                    ImageClipboard.copy(png, finish)
                } catch (t: Throwable) {
                    Log.main.warn("The system clipboard refused a picture", t)
                    finish(false)
                }
            }
        }
    }

    private fun png(path: Path): ByteArray {
        if (path.extension.equals("png", ignoreCase = true)) return Files.readAllBytes(path)
        val image = ImageIO.read(path.toFile()) ?: error("Unreadable picture $path")
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}

/** `CF_DIB` via user32/kernel32 through the game's JNA. `SetClipboardData` needs an owner window, hence the game's. */
private object WindowsClipboard {

    private const val CF_DIB = 8
    private const val GMEM_MOVEABLE = 0x0002
    private const val HEADER_BYTES = 40

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun OpenClipboard(owner: Pointer?): Boolean
        fun EmptyClipboard(): Boolean
        fun SetClipboardData(format: Int, memory: Pointer): Pointer?
        fun CloseClipboard(): Boolean
    }

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun GlobalAlloc(flags: Int, bytes: Long): Pointer?
        fun GlobalLock(memory: Pointer): Pointer?
        fun GlobalUnlock(memory: Pointer): Boolean
        fun GlobalFree(memory: Pointer): Pointer?
    }

    private val user32 by lazy { Native.load("user32", User32::class.java) }
    private val kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }

    /** Once `SetClipboardData` accepts the memory it's the clipboard's; only a refused handoff is ours to free. */
    fun copy(path: Path, window: Long): Boolean {
        val dib = dib(ImageIO.read(path.toFile()) ?: return false)
        val memory = kernel32.GlobalAlloc(GMEM_MOVEABLE, dib.size.toLong()) ?: return false
        val locked = kernel32.GlobalLock(memory) ?: run { kernel32.GlobalFree(memory); return false }
        locked.write(0, dib, 0, dib.size)
        kernel32.GlobalUnlock(memory)
        if (!user32.OpenClipboard(Pointer(window))) {
            kernel32.GlobalFree(memory)
            return false
        }
        val copied = try {
            user32.EmptyClipboard() && user32.SetClipboardData(CF_DIB, memory) != null
        } finally {
            user32.CloseClipboard()
        }
        if (!copied) kernel32.GlobalFree(memory)
        return copied
    }

    /** Bottom-up 32-bit `BITMAPINFOHEADER`. Little-endian ARGB ints are its BGRA bytes. */
    private fun dib(image: BufferedImage): ByteArray {
        val width = image.width
        val height = image.height
        val buffer = ByteBuffer.allocate(HEADER_BYTES + width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(HEADER_BYTES).putInt(width).putInt(height)
            .putShort(1).putShort(32)
            .putInt(0).putInt(width * height * 4)
            .putInt(0).putInt(0).putInt(0).putInt(0)
        val row = IntArray(width)
        for (y in height - 1 downTo 0) {
            image.getRGB(0, y, width, 1, row, 0, width)
            row.forEach(buffer::putInt)
        }
        return buffer.array()
    }
}
