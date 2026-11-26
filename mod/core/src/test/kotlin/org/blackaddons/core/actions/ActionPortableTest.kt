package org.blackaddons.core.actions

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.testing.TestPlatform
import org.blackaddons.api.util.io.Portable
import org.blackaddons.api.util.io.PortableRead
import org.blackaddons.core.config.portable.ImportPreview
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ActionPortableTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        TestPlatform.configDir = tempDir
        ActionStorage.load()
        ActionStorage.waypoints.clear()
        ActionStorage.waypointGroups.clear()
    }

    private fun preview(text: String): ImportPreview<ActionProfile> =
        ActionPortable.preview((Portable.decode(text) as PortableRead.Document).json)

    private fun apply(text: String) = (preview(text) as ImportPreview.Ready).apply()

    @Test
    fun `a shared group lands with its waypoints under fresh ids`() {
        val group = ActionGroup(name = "Route")
        val waypoint = WaypointDefinition(name = "Start", groupId = group.id)
        val text = ActionPortable.export(ActionProfile(waypointGroups = mutableListOf(group), waypoints = mutableListOf(waypoint)), asProfile = false)

        apply(text)
        apply(text)

        assertEquals(2, ActionStorage.waypointGroups.size)
        assertEquals(2, ActionStorage.waypoints.map { it.id }.distinct().size)
        ActionStorage.waypoints.zip(ActionStorage.waypointGroups).forEach { (wp, g) ->
            assertNotEquals(group.id, g.id)
            assertEquals(g.id, wp.groupId, "each copy follows its own group")
        }
    }

    @Test
    fun `a waypoint whose group stayed behind lands ungrouped`() {
        val text = ActionPortable.export(ActionProfile(waypoints = mutableListOf(WaypointDefinition(groupId = ActionGroup().id))), asProfile = false)
        apply(text)
        assertEquals(null, ActionStorage.waypoints.single().groupId)
    }

    @Test
    fun `a shared profile becomes a new profile and leaves the current one alone`() {
        val before = ActionStorage.chatActions.size
        val text = ActionPortable.export(ActionProfile(name = "Dungeons", shortcuts = mutableListOf(ShortcutAction(alias = "wf"))), asProfile = true)

        apply(text)

        assertEquals(2, ActionStorage.manifest.profiles.size)
        assertEquals("Dungeons", ActionStorage.activeProfile.name)
        assertEquals("wf", ActionStorage.shortcuts.single().alias)
        ActionStorage.selectProfile("default")
        assertEquals(before, ActionStorage.chatActions.size)
        assertTrue(ActionStorage.shortcuts.isEmpty())
    }

    @Test
    fun `nulls fall back to defaults and an unknown step type is refused`() {
        val nulls = JsonObject().apply {
            add("chatActions", JsonParser.parseString("""[{"pattern":"hi","actions":null}, null]"""))
            add("waypoints", JsonNull.INSTANCE)
        }
        val ready = assertInstanceOf(ImportPreview.Ready::class.java, ActionPortable.preview(nulls))
        assertEquals(1, ready.rows.size)

        val newer = JsonObject().apply {
            add("chatActions", JsonParser.parseString("""[{"actions":[{"type":"TELEPORT"}]}]"""))
        }
        assertInstanceOf(ImportPreview.Failed::class.java, ActionPortable.preview(newer))
        assertInstanceOf(ImportPreview.Failed::class.java, ActionPortable.preview(JsonObject()))
    }

    @Test
    fun `a shared shortcut group lands with its shortcuts under fresh ids`() {
        ActionStorage.shortcuts.clear()
        ActionStorage.shortcutGroups.clear()
        val group = ActionGroup(name = "Keys")
        val shortcut = ShortcutAction(name = "S", groupId = group.id)
        apply(ActionPortable.export(ActionProfile(shortcutGroups = mutableListOf(group), shortcuts = mutableListOf(shortcut)), asProfile = false))

        val landed = ActionStorage.shortcutGroups.single()
        assertNotEquals(group.id, landed.id)
        assertEquals(landed.id, ActionStorage.shortcuts.single().groupId)
    }

    @Test
    fun `a share string carries group ids but no action ids`() {
        val group = ActionGroup(name = "Route")
        val waypoint = WaypointDefinition(name = "Start", groupId = group.id)
        val text = ActionPortable.export(ActionProfile(waypointGroups = mutableListOf(group), waypoints = mutableListOf(waypoint)), asProfile = false)
        val json = (Portable.decode(text) as PortableRead.Document).json

        val shared = json.getAsJsonArray("waypoints").single().asJsonObject
        assertTrue(!shared.has("id"))
        assertEquals(group.id.toString(), shared.get("groupId").asString)
        assertEquals(group.id.toString(), json.getAsJsonArray("waypointGroups").single().asJsonObject.get("id").asString)
    }
}
