package org.blackaddons.api.util.io
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Atomic writes: temp sibling then move, so a crash or full disk leaves the old file intact. I/O errors propagate. */
object Disk {

    // Windows rejects concurrent replace-moves onto the same target.
    private val writeLock = Any()

    /** Null when missing. */
    fun read(path: Path): String? = if (Files.exists(path)) Files.readString(path) else null

    fun write(path: Path, text: String) = write(path) { Files.writeString(it, text) }

    fun write(path: Path, bytes: ByteArray) = write(path) { Files.write(it, bytes) }

    /** [writeTemp] fills the temp file; a throw (including a failed check) leaves the old file alone. */
    fun write(path: Path, writeTemp: (Path) -> Unit) = writeAll { file(path, writeTemp) }

    /**
     * Writes every temp file first, then replaces targets. A throw before that changes nothing; a crash
     * during the final moves can leave a mix of old and new files, never a half-written one.
     */
    fun writeAll(files: Batch.() -> Unit) {
        val staged = Batch().apply(files).staged
        synchronized(writeLock) {
            val temps = ArrayList<Pair<Path, Path>>(staged.size)
            try {
                for ((target, writeTemp) in staged) {
                    target.parent?.let { Files.createDirectories(it) }
                    val tmp = Files.createTempFile(target.parent, "${target.fileName}.", ".tmp")
                    temps += tmp to target
                    writeTemp(tmp)
                }
                for ((tmp, target) in temps) move(tmp, target)
            } finally {
                temps.forEach { (tmp, _) -> Files.deleteIfExists(tmp) }
            }
        }
    }

    class Batch internal constructor() {
        internal val staged = ArrayList<Pair<Path, (Path) -> Unit>>()

        fun file(path: Path, writeTemp: (Path) -> Unit) {
            staged += path to writeTemp
        }
    }

    private fun move(tmp: Path, target: Path) {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            // No atomic rename across filesystems (symlinks, network mounts); a replace still beats writing in place.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
