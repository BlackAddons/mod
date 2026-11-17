package org.blackaddons.core.storage

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.blackaddons.api.testing.TestPlatform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.concurrent.thread

class FileAddonStoragesTest {

    @TempDir
    lateinit var tempDir: Path

    // Stores are cached per id for the JVM's whole life, so each test uses a fresh id.
    private val id = "test-" + UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun setUp() {
        TestPlatform.configDir = tempDir
    }

    @Test
    fun `values round trip and reach the file`() {
        val store = FileAddonStorages.forAddon(id)
        store.putString("name", "x")
        store.putInt("count", 3)
        assertEquals("x", store.getString("name", null))
        assertEquals(3, store.getInt("count", 0))
        assertEquals(0, store.getInt("name", 0), "a string is not an int")
        assertTrue(Files.readString(tempDir.resolve("addons/$id.json")).contains("\"count\": 3"))
    }

    @Test
    fun `json goes in and out as a copy`() {
        val store = FileAddonStorages.forAddon(id)
        val mine = JsonObject().apply { addProperty("a", 1) }
        store.putJson("obj", mine)
        mine.addProperty("a", 2)
        store.getJson("obj")!!.asJsonObject.add("b", JsonPrimitive(3))
        assertEquals("{\"a\":1}", store.getJson("obj").toString())
    }

    @Test
    fun `reading keys while another thread writes does not throw`() {
        val store = FileAddonStorages.forAddon(id)
        val writer = thread { repeat(300) { store.putInt("k$it", it) } }
        while (writer.isAlive) store.keys()
        writer.join()
        assertEquals(300, store.keys().size)
    }
}
