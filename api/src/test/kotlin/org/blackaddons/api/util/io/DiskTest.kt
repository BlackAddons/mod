package org.blackaddons.api.util.io
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiskTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `concurrent writes to one file do not share a temporary file`() {
        val destination = tempDir.resolve("collection.json")
        val workers = 16
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val complete = CountDownLatch(workers)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val executor = Executors.newFixedThreadPool(workers)

        repeat(workers) { index ->
            executor.execute {
                ready.countDown()
                try {
                    start.await()
                    Disk.write(destination, "write-$index")
                } catch (t: Throwable) {
                    failures += t
                } finally {
                    complete.countDown()
                }
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(complete.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertTrue(failures.isEmpty(), "concurrent atomic writes failed: $failures")
        assertTrue(Files.readString(destination).startsWith("write-"))
        assertTrue(Files.list(tempDir).use { files -> files.noneMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun `a batch replaces every file once all writers return`() {
        val a = tempDir.resolve("a.txt").also { Files.writeString(it, "old a") }
        val b = tempDir.resolve("nested/b.txt")

        Disk.writeAll {
            file(a) { Files.writeString(it, "new a") }
            file(b) { Files.writeString(it, "new b") }
        }

        assertEquals("new a", Files.readString(a))
        assertEquals("new b", Files.readString(b))
        assertNoTemporaryFiles()
    }

    @Test
    fun `a batch whose last writer throws changes nothing on disk`() {
        val a = tempDir.resolve("a.txt").also { Files.writeString(it, "old a") }
        val b = tempDir.resolve("b.txt")

        assertThrows(IllegalStateException::class.java) {
            Disk.writeAll {
                file(a) { Files.writeString(it, "new a") }
                file(b) { check(false) { "written file failed its check" } }
            }
        }

        assertEquals("old a", Files.readString(a))
        assertFalse(Files.exists(b))
        assertNoTemporaryFiles()
    }

    @Test
    fun `a single write that fails its own check keeps the old file`() {
        val file = tempDir.resolve("config.json").also { Files.writeString(it, "old") }

        assertThrows(IllegalStateException::class.java) {
            Disk.write(file) {
                Files.writeString(it, "torn")
                check(Files.readString(it) == "complete") { "validation failed" }
            }
        }

        assertEquals("old", Files.readString(file))
        assertNoTemporaryFiles()
    }

    private fun assertNoTemporaryFiles() = Files.walk(tempDir).use { files ->
        assertTrue(files.noneMatch { it.fileName.toString().endsWith(".tmp") }, "temporary files left behind")
    }
}
