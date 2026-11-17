package org.blackaddons.platform.compat

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import net.minecraft.util.Util
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.api.util.packInts
import org.blackaddons.api.util.packedHigh
import org.blackaddons.core.log.Log

object ImageClipboard {

    fun copy(png: ByteArray, onDone: (Boolean) -> Unit) {
        if (Util.getPlatform() == Util.OS.OSX) onDone(CocoaClipboard.copy(png)) else XcbClipboard.copy(png, onDone)
    }
}

private object CocoaClipboard {

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
        fun objc_msgSend(receiver: Pointer, selector: Pointer): Pointer?
        fun objc_msgSend(receiver: Pointer, selector: Pointer, utf8: String): Pointer?
        fun objc_msgSend(receiver: Pointer, selector: Pointer, bytes: ByteArray, length: Long): Pointer?
        fun objc_msgSend(receiver: Pointer, selector: Pointer, first: Pointer, second: Pointer): Byte
    }

    private val objc by lazy { Native.load("objc", ObjC::class.java) }

    fun copy(png: ByteArray): Boolean {
        val pool = send(send(type("NSAutoreleasePool"), "alloc"), "init")
        try {
            val pasteboard = send(type("NSPasteboard"), "generalPasteboard")
            objc.objc_msgSend(pasteboard, objc.sel_registerName("clearContents"))
            val data = objc.objc_msgSend(type("NSData"), objc.sel_registerName("dataWithBytes:length:"), png, png.size.toLong())
                ?: return false
            val kind = objc.objc_msgSend(type("NSString"), objc.sel_registerName("stringWithUTF8String:"), "public.png")
                ?: return false
            return objc.objc_msgSend(pasteboard, objc.sel_registerName("setData:forType:"), data, kind) != 0.toByte()
        } finally {
            send(pool, "drain")
        }
    }

    private fun type(name: String): Pointer = objc.objc_getClass(name) ?: error("No Objective-C class $name")

    private fun send(receiver: Pointer, selector: String): Pointer =
        objc.objc_msgSend(receiver, objc.sel_registerName(selector)) ?: error("$selector returned nil")
}

@Structure.FieldOrder("data", "rem", "index")
internal class XcbScreenIterator : Structure(), Structure.ByValue {
    @JvmField var data: Pointer? = null
    @JvmField var rem: Int = 0
    @JvmField var index: Int = 0
}

private object XcbClipboard {

    private const val PROPERTY_NOTIFY = 28
    private const val SELECTION_CLEAR = 29
    private const val SELECTION_REQUEST = 30
    private const val SELECTION_NOTIFY = 31
    private const val PROPERTY_DELETE: Byte = 1
    private const val PROP_MODE_REPLACE: Byte = 0
    private const val WINDOW_CLASS_INPUT_ONLY: Short = 2
    private const val CW_EVENT_MASK = 0x800
    private const val EVENT_MASK_NONE = 0
    private const val EVENT_MASK_PROPERTY_CHANGE = 0x400000
    private const val ATOM_ATOM = 4
    private const val CURRENT_TIME = 0
    private const val EVENT_BYTES = 32L
    private const val REPLY_VALUE_OFFSET = 8L
    private const val REQUEST_HEADER_BYTES = 32L

    @Suppress("FunctionName")
    private interface Xcb : Library {
        fun xcb_connect(display: String?, screen: Pointer?): Pointer
        fun xcb_connection_has_error(connection: Pointer): Int
        fun xcb_disconnect(connection: Pointer)
        fun xcb_get_setup(connection: Pointer): Pointer
        fun xcb_setup_roots_iterator(setup: Pointer): XcbScreenIterator
        fun xcb_generate_id(connection: Pointer): Int
        fun xcb_create_window(
            connection: Pointer, depth: Byte, window: Int, parent: Int, x: Short, y: Short, width: Short, height: Short,
            border: Short, windowClass: Short, visual: Int, valueMask: Int, values: Pointer?,
        ): Int
        fun xcb_intern_atom(connection: Pointer, onlyIfExists: Byte, length: Short, name: String): Int
        fun xcb_intern_atom_reply(connection: Pointer, cookie: Int, error: Pointer?): Pointer?
        fun xcb_set_selection_owner(connection: Pointer, owner: Int, selection: Int, time: Int): Int
        fun xcb_get_selection_owner(connection: Pointer, selection: Int): Int
        fun xcb_get_selection_owner_reply(connection: Pointer, cookie: Int, error: Pointer?): Pointer?
        fun xcb_change_property(
            connection: Pointer, mode: Byte, window: Int, property: Int, type: Int, format: Byte, length: Int, data: Pointer,
        ): Int
        fun xcb_change_window_attributes(connection: Pointer, window: Int, valueMask: Int, values: Pointer): Int
        fun xcb_send_event(connection: Pointer, propagate: Byte, destination: Int, eventMask: Int, event: Pointer): Int
        fun xcb_get_maximum_request_length(connection: Pointer): Int
        fun xcb_flush(connection: Pointer): Int
        fun xcb_wait_for_event(connection: Pointer): Pointer?
    }

    private val xcb by lazy { Native.load("libxcb.so.1", Xcb::class.java) }

    fun copy(png: ByteArray, onDone: (Boolean) -> Unit) {
        Workers.start("x11-clipboard") {
            var reported = false
            try {
                own(png) { owned ->
                    reported = true
                    onDone(owned)
                }
            } catch (t: Throwable) {
                Log.main.warn("X11 clipboard owner stopped", t)
                if (!reported) onDone(false)
            }
        }
    }

    private fun own(png: ByteArray, report: (Boolean) -> Unit) {
        val connection = xcb.xcb_connect(null, null)
        try {
            if (xcb.xcb_connection_has_error(connection) != 0) return report(false)
            val root = xcb.xcb_setup_roots_iterator(xcb.xcb_get_setup(connection)).data?.getInt(0) ?: return report(false)
            val window = xcb.xcb_generate_id(connection)
            xcb.xcb_create_window(connection, 0, window, root, 0, 0, 1, 1, 0, WINDOW_CLASS_INPUT_ONLY, 0, 0, null)
            val clipboard = atom(connection, "CLIPBOARD")
            xcb.xcb_set_selection_owner(connection, window, clipboard, CURRENT_TIME)
            val owner = xcb.xcb_get_selection_owner_reply(connection, xcb.xcb_get_selection_owner(connection, clipboard), null)
            val owned = owner != null && owner.getInt(REPLY_VALUE_OFFSET) == window
            owner?.let(::free)
            report(owned)
            if (owned) Owner(connection, png).serve()
        } finally {
            xcb.xcb_disconnect(connection)
        }
    }

    private fun atom(connection: Pointer, name: String): Int {
        val reply = xcb.xcb_intern_atom_reply(connection, xcb.xcb_intern_atom(connection, 0, name.length.toShort(), name), null)
            ?: error("Could not intern $name")
        return reply.getInt(REPLY_VALUE_OFFSET).also { free(reply) }
    }

    private fun free(pointer: Pointer) = Native.free(Pointer.nativeValue(pointer))

    private class Owner(private val connection: Pointer, png: ByteArray) {

        private val targets = atom(connection, "TARGETS")
        private val pngType = atom(connection, "image/png")
        private val incr = atom(connection, "INCR")
        private val size = png.size
        private val data = Memory(size.toLong()).apply { write(0, png, 0, size) }
        private val chunk = ((xcb.xcb_get_maximum_request_length(connection).toLong() and 0xFFFFFFFFL) * 4 - REQUEST_HEADER_BYTES)
            .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        private val transfers = HashMap<Long, Int>()

        fun serve() {
            while (true) {
                val event = xcb.xcb_wait_for_event(connection) ?: return
                try {
                    when (event.getByte(0).toInt() and 0x7F) {
                        SELECTION_CLEAR -> return
                        SELECTION_REQUEST -> answer(event)
                        PROPERTY_NOTIFY -> continueTransfer(event)
                    }
                } finally {
                    free(event)
                }
                xcb.xcb_flush(connection)
            }
        }

        private fun answer(request: Pointer) {
            val requestor = request.getInt(12)
            val target = request.getInt(20)
            val property = request.getInt(24).takeIf { it != 0 } ?: target
            val stored = when (target) {
                targets -> {
                    putInts(requestor, property, ATOM_ATOM, targets, pngType)
                    true
                }
                pngType -> {
                    startTransfer(requestor, property)
                    true
                }
                else -> false
            }
            val reply = Memory(EVENT_BYTES).apply {
                clear()
                setByte(0, SELECTION_NOTIFY.toByte())
                setInt(4, request.getInt(4))
                setInt(8, requestor)
                setInt(12, request.getInt(16))
                setInt(16, target)
                setInt(20, if (stored) property else 0)
            }
            xcb.xcb_send_event(connection, 0, requestor, EVENT_MASK_NONE, reply)
        }

        private fun startTransfer(requestor: Int, property: Int) {
            if (size <= chunk) {
                xcb.xcb_change_property(connection, PROP_MODE_REPLACE, requestor, property, pngType, 8, size, data)
                return
            }
            watch(requestor, EVENT_MASK_PROPERTY_CHANGE)
            putInts(requestor, property, incr, size)
            transfers[key(requestor, property)] = 0
        }

        private fun continueTransfer(event: Pointer) {
            if (event.getByte(16) != PROPERTY_DELETE) return
            val window = event.getInt(4)
            val property = event.getInt(8)
            val key = key(window, property)
            val offset = transfers[key] ?: return
            val length = minOf(chunk, size - offset)
            val bytes = if (length == 0) data else data.share(offset.toLong(), length.toLong())
            xcb.xcb_change_property(connection, PROP_MODE_REPLACE, window, property, pngType, 8, length, bytes)
            if (length > 0) {
                transfers[key] = offset + length
                return
            }
            transfers.remove(key)
            if (transfers.keys.none { it.packedHigh() == window }) watch(window, EVENT_MASK_NONE)
        }

        private fun putInts(window: Int, property: Int, type: Int, vararg values: Int) {
            val memory = Memory(values.size * 4L)
            values.forEachIndexed { index, value -> memory.setInt(index * 4L, value) }
            xcb.xcb_change_property(connection, PROP_MODE_REPLACE, window, property, type, 32, values.size, memory)
        }

        private fun watch(window: Int, mask: Int) {
            xcb.xcb_change_window_attributes(connection, window, CW_EVENT_MASK, Memory(4).apply { setInt(0, mask) })
        }

        private fun key(window: Int, property: Int) = packInts(window, property)
    }
}
