package org.blackaddons.core.discord

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.util.io.Gsons
import java.io.Closeable
import java.io.IOException
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path

/**
 * One connection to the local Discord client: a named pipe on Windows and a Unix socket elsewhere. Frames are
 * `[opcode][length]` little-endian ints followed by JSON. Not thread-safe, one worker owns it.
 *
 * Every command is written and its reply read on the same thread. A Windows pipe opened with [RandomAccessFile] is
 * synchronous, so a read waiting on another thread would block every write.
 */
internal class DiscordIpc private constructor(private val pipe: Pipe) : Closeable {

    fun send(payload: Any, opcode: Int = OP_FRAME): JsonObject {
        write(opcode, Gsons.compact.toJson(payload))
        while (true) {
            val (op, body) = read()
            when (op) {
                OP_PING -> write(OP_PONG, body.toString())
                OP_CLOSE -> throw IOException("Discord closed the connection: $body")
                else -> return body
            }
        }
    }

    private fun write(opcode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val frame = ByteBuffer.allocate(HEADER + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode).putInt(bytes.size).put(bytes)
        pipe.write(frame.array())
    }

    private fun read(): Pair<Int, JsonObject> {
        val header = ByteBuffer.wrap(pipe.read(HEADER)).order(ByteOrder.LITTLE_ENDIAN)
        val opcode = header.int
        val length = header.int
        if (length !in 0..MAX_FRAME) throw IOException("Bad frame length $length")
        val body = pipe.read(length).toString(Charsets.UTF_8)
        return opcode to (JsonParser.parseString(body.ifEmpty { "{}" }) as? JsonObject ?: JsonObject())
    }

    override fun close() {
        runCatching { pipe.close() }
    }

    private interface Pipe : Closeable {
        fun write(bytes: ByteArray)

        fun read(count: Int): ByteArray
    }

    private class WindowsPipe(path: String) : Pipe {
        private val file = RandomAccessFile(path, "rw")

        override fun write(bytes: ByteArray) = file.write(bytes)

        override fun read(count: Int): ByteArray = ByteArray(count).also(file::readFully)

        override fun close() = file.close()
    }

    private class UnixPipe(path: Path) : Pipe {
        private val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            .apply { connect(UnixDomainSocketAddress.of(path)) }

        override fun write(bytes: ByteArray) {
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
        }

        override fun read(count: Int): ByteArray {
            val buffer = ByteBuffer.allocate(count)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw IOException("Discord closed the socket")
            }
            return buffer.array()
        }

        override fun close() = channel.close()
    }

    companion object {
        const val OP_HANDSHAKE = 0
        const val OP_FRAME = 1
        private const val OP_CLOSE = 2
        private const val OP_PING = 3
        private const val OP_PONG = 4

        private const val HEADER = 8
        private const val MAX_FRAME = 1 shl 20
        private const val SLOTS = 10

        /**
         * Flatpak and Snap Discord, as well as Vesktop, put the socket in their own sandbox folder under the runtime
         * folder.
         */
        private val SANDBOX_DIRS = listOf("", "app/com.discordapp.Discord", "snap.discord", ".flatpak/dev.vencord.Vesktop/xdg-run")

        /** The first slot a Discord client answers on, or null when none is running. */
        fun open(): DiscordIpc? {
            for (slot in 0 until SLOTS) {
                val pipe = runCatching { openSlot(slot) }.getOrNull() ?: continue
                return DiscordIpc(pipe)
            }
            return null
        }

        private fun openSlot(slot: Int): Pipe? {
            val name = "discord-ipc-$slot"
            if (System.getProperty("os.name").startsWith("Windows")) return WindowsPipe("\\\\.\\pipe\\$name")
            val path = unixRoots().flatMap { root -> SANDBOX_DIRS.map { root.resolve(it).resolve(name) } }
                .firstOrNull(Files::exists) ?: return null
            return UnixPipe(path)
        }

        private fun unixRoots(): List<Path> =
            (listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNull(System::getenv) + "/tmp")
                .distinct().map(Path::of)
    }
}
