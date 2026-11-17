package org.blackaddons.platform.compat

import org.lwjgl.sdl.SDLClipboard
import org.lwjgl.sdl.SDL_ClipboardCleanupCallback
import org.lwjgl.sdl.SDL_ClipboardDataCallback
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Pointer

object ImageClipboard {

    private const val PNG = "image/png"

    private val data = SDL_ClipboardDataCallback.create { userdata, mimeType, size ->
        if (mimeType == MemoryUtil.NULL || MemoryUtil.memUTF8(mimeType) != PNG) {
            MemoryUtil.memPutAddress(size, 0)
            MemoryUtil.NULL
        } else {
            MemoryUtil.memPutAddress(size, MemoryUtil.memGetAddress(userdata))
            userdata + Pointer.POINTER_SIZE
        }
    }

    private val cleanup = SDL_ClipboardCleanupCallback.create { userdata -> MemoryUtil.nmemFree(userdata) }

    fun copy(png: ByteArray, onDone: (Boolean) -> Unit) {
        val memory = MemoryUtil.nmemAlloc((Pointer.POINTER_SIZE + png.size).toLong())
        if (memory == MemoryUtil.NULL) return onDone(false)
        MemoryUtil.memPutAddress(memory, png.size.toLong())
        MemoryUtil.memByteBuffer(memory + Pointer.POINTER_SIZE, png.size).put(png)
        val set = MemoryStack.stackPush().use { stack ->
            SDLClipboard.SDL_SetClipboardData(data, cleanup, memory, stack.pointers(stack.UTF8(PNG)))
        }
        onDone(set)
    }
}
