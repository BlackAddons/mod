package org.blackaddons.core.actions

import com.google.gson.JsonObject
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.api.commands.CommandHost
import org.blackaddons.api.commands.DynamicAlias
import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.input.KeyNames
import org.blackaddons.api.input.Keys
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.skyblock.F7SubLocation
import org.blackaddons.api.testing.TestPlatform
import org.blackaddons.core.actions.execution.ActionInterpolator
import org.blackaddons.core.actions.handlers.ChatActionHandler
import org.blackaddons.core.actions.handlers.TempWaypoints
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.actions.steps.TempWaypointStep
import org.blackaddons.core.actions.steps.TextOverlayStep
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ActionStorageTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        TestPlatform.configDir = tempDir
        ActionStorage.load()
    }

    @Test
    fun `a shortcut saved as a raw GLFW key loads as the native key and is saved back by name`() {
        TestPlatform.keyNames = object : KeyNames {
            override fun keyName(keyCode: Int) = ""
            override fun isKeyDown(keyCode: Int) = false
            override fun isMouseDown(button: Int) = false
            override fun code(name: String) = if (name == "key.keyboard.v") 25 else Keys.UNKNOWN
            override fun name(code: Int) = if (code == 25) "key.keyboard.v" else "key.keyboard.unknown"
        }
        val file = tempDir.resolve("actions").resolve("default.json")
        Files.writeString(file, """{"id":"default","name":"Default","shortcuts":[{"alias":"f7","keyCode":86,"keyType":"KEYBOARD"}]}""")
        ActionStorage.load()

        assertEquals(InputBinding.keyboard(25), ActionStorage.shortcuts.single().key)
        ActionStorage.save()
        val saved = Files.readString(file)
        assertTrue("key.keyboard.v" in saved, saved)
        assertFalse("keyCode" in saved, saved)
    }

    @Test
    fun `an unreadable manifest keeps the profile it points at from being overwritten`() {
        val actionsDir = tempDir.resolve("actions")
        val profile = actionsDir.resolve("default.json")
        val original = """{"id":"default","name":"Default","waypoints":[{"name":"Mine"}]}"""
        Files.writeString(profile, original)
        // Present but unreadable, because reading a directory throws.
        Files.delete(actionsDir.resolve("profiles.json"))
        Files.createDirectory(actionsDir.resolve("profiles.json"))

        ActionStorage.load()
        ActionStorage.waypoints.add(WaypointDefinition(name = "Edit"))
        ActionStorage.save()

        assertEquals(original, Files.readString(profile))
    }

    @Test
    fun `load initializes default profile when storage is empty`() {
        assertEquals("default", ActionStorage.manifest.activeProfileId)
        assertEquals(1, ActionStorage.manifest.profiles.size)
        assertEquals("Default", ActionStorage.activeProfile.name)
        assertEquals(3, ActionStorage.chatActions.size)
        assertTrue(ActionStorage.chatActions.none(ChatAction::enabled))

        val posMsg = ActionStorage.waypointGroups.single { it.name == "Pos Msg" }
        assertFalse(posMsg.enabled)
        assertEquals(listOf("Catacombs"), posMsg.areaFilters)
        val posMessages = ActionStorage.waypoints.filter { it.groupId == posMsg.id }
        assertEquals(listOf("at ss", "ee2", "ee3", "at core", "at mid"), posMessages.map { it.name })
        val trigger = posMessages[1].actions.single()
        assertEquals(TriggeredBy.ANYONE, trigger.triggeredBy)
        val (overlay, chat) = trigger.actions
        assertEquals(TextOverlayStep.Params("{player} at ee2", 1f), overlay.paramsOf(TextOverlayStep))
        assertEquals(TriggeredBy.ANYONE, overlay.triggeredBy)
        assertTrue(overlay.enabled)
        assertEquals("/pc at ee2", chat.paramsOf(SendMessageStep)?.message)
        assertEquals(TriggeredBy.ME, chat.triggeredBy)
        assertFalse(chat.enabled)

        assertEquals(
            listOf(F7SubLocation.SS, F7SubLocation.EE2, F7SubLocation.EE3, F7SubLocation.CORE, F7SubLocation.MID),
            posMessages.map { DungeonGeometry.f7SubLocation(it.x, it.y, it.z) },
            "a Pos Msg default drifted out of its DungeonGeometry sublocation",
        )

        val actionsDir = tempDir.resolve("actions")
        assertTrue(Files.exists(actionsDir), "Actions directory should be created")
        assertTrue(Files.exists(actionsDir.resolve("profiles.json")), "Manifest file should be created")
        assertTrue(Files.exists(actionsDir.resolve("default.json")), "Default profile file should be created")
    }

    @Test
    fun `the temp waypoint preset reads a position shared in chat`() {
        val preset = ActionStorage.chatActions.single { action -> action.actions.any { it.type === TempWaypointStep } }
        assertFalse(preset.enabled)
        assertTrue(preset.ignoreOwnMessages)
        val step = checkNotNull(preset.actions.single().paramsOf(TempWaypointStep))
        val pattern = ChatActionHandler.compiled(preset.pattern)!!

        fun read(line: String): Pair<List<Double>?, String> {
            val matcher = pattern.matcher(line)
            assertTrue(matcher.find(), line)
            val groups = Array(matcher.groupCount() + 1) { matcher.group(it) ?: "" }
            return TempWaypoints.parse(ActionInterpolator.interpolate(step.waypointCoords, groups, "me")) to
                ActionInterpolator.interpolate(step.message, groups, "me")
        }

        assertEquals(
            listOf(32.5, 76.0, -26.5) to "Cuquinillo777 | +15 Fishing Speed Hotspot",
            read("[282] ⚓ [MVP+] Cuquinillo777 ●: x: 32, y: 76, z: -27 | +15 Fishing Speed Hotspot | @sXrJ1MmSZV"),
        )
        assertEquals(listOf(1.5, 2.0, 3.5) to "Bob", read("Party > [MVP+] Bob: x: 1, y: 2, z: 3"))
        assertEquals(listOf(1.5, 2.0, 3.5) to "Bob", read("Guild > [VIP] Bob [Officer]: meet x: 1, y: 2, z: 3"))
        assertEquals(listOf(1.5, 0.0, 1.5) to "Bob", read("[MVP+] Bob: x 1.4 y 0.6 z 1.4"))
        assertEquals(listOf(32.5, 76.0, -21.5) to "Bob", read("[4] [MVP+] Bob: x 32 y 76 z -22 @aaaa1q2312"))
        assertNull(TempWaypoints.parse("x: 1, y: 2"))
    }

    @Test
    fun `createProfile creates a new profile and sets it active`() {
        val newProfile = ActionStorage.createProfile("Mining Profile", copyCurrent = false)

        assertEquals("Mining Profile", newProfile.name)
        assertEquals(newProfile.id, ActionStorage.manifest.activeProfileId)
        assertEquals(2, ActionStorage.manifest.profiles.size)

        val profileFile = tempDir.resolve("actions").resolve("${newProfile.id}.json")
        assertTrue(Files.exists(profileFile), "New profile file should exist on disk")
    }

    @Test
    fun `createProfile with copyCurrent copies chat actions and waypoints`() {
        ActionStorage.waypoints.add(WaypointDefinition(name = "Test Waypoint"))
        ActionStorage.save()

        val copiedProfile = ActionStorage.createProfile("Copied Profile", copyCurrent = true)

        assertEquals(ActionStorage.waypoints.size, copiedProfile.waypoints.size)
        assertEquals("Test Waypoint", copiedProfile.waypoints.last().name)
    }

    @Test
    fun `selectProfile switches active profile`() {
        val p2 = ActionStorage.createProfile("Second Profile")
        val defaultId = "default"

        ActionStorage.selectProfile(defaultId)
        assertEquals(defaultId, ActionStorage.manifest.activeProfileId)
        assertEquals("Default", ActionStorage.activeProfile.name)

        ActionStorage.selectProfile(p2.id)
        assertEquals(p2.id, ActionStorage.manifest.activeProfileId)
        assertEquals("Second Profile", ActionStorage.activeProfile.name)
    }

    @Test
    fun `renameProfile updates profile name in manifest and active profile`() {
        ActionStorage.renameProfile("default", "Renamed Default")

        assertEquals("Renamed Default", ActionStorage.activeProfile.name)
        assertEquals("Renamed Default", ActionStorage.manifest.profiles.first { it.id == "default" }.name)
    }

    @Test
    fun `deleteProfile fails when only one profile exists`() {
        val deleted = ActionStorage.deleteProfile("default")
        assertFalse(deleted, "Should not delete the single remaining profile")
        assertEquals(1, ActionStorage.manifest.profiles.size)
    }

    @Test
    fun `deleteProfile removes profile and switches active profile`() {
        val p2 = ActionStorage.createProfile("Second Profile")
        val p2Id = p2.id

        val deleted = ActionStorage.deleteProfile(p2Id)
        assertTrue(deleted, "Deleting second profile should succeed")
        assertEquals(1, ActionStorage.manifest.profiles.size)
        assertEquals("default", ActionStorage.manifest.activeProfileId)

        val p2File = tempDir.resolve("actions").resolve("$p2Id.json")
        assertFalse(Files.exists(p2File), "Deleted profile JSON should be removed from disk")
    }

    @Test
    fun `deleteProfile removes its graph boards and leaves the other profiles' alone`() {
        val p2Id = ActionStorage.createProfile("Second Profile").id
        val board = Files.createDirectories(ActionStorage.graphDir.resolve("chat"))
        Files.writeString(board.resolve("$p2Id.json"), "{}")
        Files.writeString(board.resolve("default.json"), "{}")

        assertTrue(ActionStorage.deleteProfile(p2Id))

        assertFalse(Files.exists(board.resolve("$p2Id.json")))
        assertTrue(Files.exists(board.resolve("default.json")))
    }

    @Test
    fun `a shortcut in a disabled group has no alias until the group is switched on`() {
        var synced = emptyList<String>()
        TestPlatform.commands = object : CommandHost {
            override fun register(name: String, vararg aliases: String, build: CommandBuilder.() -> Unit) = Unit
            override fun syncDynamicAliases(aliases: List<DynamicAlias>) {
                synced = aliases.map { it.name }
            }
        }
        val group = ActionGroup(enabled = false).also(ActionStorage.shortcutGroups::add)
        ActionStorage.shortcuts += ShortcutAction(groupId = group.id, alias = "f7", enabled = true)
        ActionStorage.save()
        assertEquals(emptyList<String>(), synced)

        group.enabled = true
        ActionStorage.save()
        assertEquals(listOf("f7"), synced)
    }

    @Test
    fun `load rejects profile IDs that escape the actions directory`() {
        val actionsDir = tempDir.resolve("actions")
        val outsideFile = tempDir.resolve("outside.json")
        Files.writeString(outsideFile, "must stay untouched")
        Files.writeString(
            actionsDir.resolve("profiles.json"),
            """
            {
              "activeProfileId": "../outside",
              "profiles": [
                { "id": "default", "name": "Default" },
                { "id": "../outside", "name": "Escaped" }
              ]
            }
            """.trimIndent(),
        )
        Files.writeString(actionsDir.resolve("default.json"), """{ "id": "../outside", "name": "Escaped" }""")

        ActionStorage.load()
        ActionStorage.save()

        assertEquals(listOf("default"), ActionStorage.manifest.profiles.map { it.id })
        assertEquals("default", ActionStorage.activeProfile.id)
        assertFalse(ActionStorage.deleteProfile("../outside"))
        assertEquals("must stay untouched", Files.readString(outsideFile))
    }

    @Test
    fun `deleteGroup removes the group and unlinks its waypoints`() {
        val group = ActionGroup(name = "M7 Phase 1").also(ActionStorage.waypointGroups::add)

        val wp = WaypointDefinition(name = "SS Term", groupId = group.id)
        ActionStorage.waypoints.add(wp)
        ActionStorage.save()

        ActionStorage.deleteGroup(group.id)
        assertTrue(ActionStorage.waypointGroups.none { it.id == group.id })
        assertEquals(null, wp.groupId)
    }

    @Test
    fun `createProfile with copyCurrent copies waypoint groups`() {
        val group = ActionGroup(name = "Dungeon Group").also(ActionStorage.waypointGroups::add)
        val wp = WaypointDefinition(name = "WP", groupId = group.id)
        ActionStorage.waypoints.add(wp)
        ActionStorage.save()

        val copiedProfile = ActionStorage.createProfile("Copied Profile", copyCurrent = true)

        assertEquals("Dungeon Group", copiedProfile.waypointGroups.single { it.id == group.id }.name)
        assertEquals(group.id, copiedProfile.waypoints.single { it.name == "WP" }.groupId)
    }

    @Test
    fun `steps survive profile persistence, even of an addon that is not installed`() {
        val missing = ActionJson.plain.fromJson("""{"type":"ALIGN","dontWaitForPerfectAlign":true}""", ActionStep::class.java)
        ActionStorage.chatActions += ChatAction(
            pattern = "lock",
            actions = mutableListOf(ActionStep.of(TestSteps.TAP, TestSteps.Params("hi")).apply { set(TestSteps.Flag, true) }, missing),
        )
        ActionStorage.save()

        ActionStorage.load()

        val (tap, align) = ActionStorage.chatActions.single { it.pattern == "lock" }.actions
        assertTrue(tap.has(TestSteps.Flag))
        assertEquals(TestSteps.Params("hi"), tap.paramsOf(TestSteps.TAP))
        assertEquals("ALIGN", align.typeId)
        assertEquals(true, (align.params as JsonObject)["dontWaitForPerfectAlign"].asBoolean)
    }

    @Test
    fun `a manifest that cannot be parsed is not overwritten`() {
        ActionStorage.chatActions.add(ChatAction(pattern = "keep me"))
        ActionStorage.save()

        val manifest = tempDir.resolve("actions").resolve("profiles.json")
        val profile = tempDir.resolve("actions").resolve("default.json")
        val profileBefore = Files.readString(profile)
        val broken = "{ not valid json"
        Files.writeString(manifest, broken)

        ActionStorage.load()
        ActionStorage.save()

        assertEquals(broken, Files.readString(manifest), "A manifest we could not read must survive the next save")
        assertEquals(profileBefore, Files.readString(profile), "Its profile must survive with it")
    }

    @Test
    fun `a profile that cannot be parsed is not overwritten`() {
        ActionStorage.chatActions.add(ChatAction(pattern = "keep me"))
        ActionStorage.save()

        val profile = tempDir.resolve("actions").resolve("default.json")
        val broken = "{ not valid json"
        Files.writeString(profile, broken)

        ActionStorage.load()
        assertTrue(ActionStorage.chatActions.isEmpty(), "An unreadable profile loads as empty")

        ActionStorage.save()

        assertEquals(broken, Files.readString(profile), "But the empty state must not be written over it")
    }

    @Test
    fun `a chat group gates its actions, survives a reload, and deleting it ungroups them`() {
        val group = ActionGroup(name = "Off")
        val grouped = ChatAction(pattern = "a", groupId = group.id)
        val loose = ChatAction(pattern = "b")
        ActionStorage.chatGroups += group
        ActionStorage.chatActions += listOf(grouped, loose)
        ActionStorage.save()

        assertTrue(ActionStorage.inActiveGroup(ActionStorage.chatGroups, grouped))
        group.enabled = false
        assertFalse(ActionStorage.inActiveGroup(ActionStorage.chatGroups, grouped))
        assertTrue(ActionStorage.inActiveGroup(ActionStorage.chatGroups, loose))
        ActionStorage.save()

        ActionStorage.load()
        val reloaded = ActionStorage.chatActions.single { it.pattern == "a" }
        assertEquals(group.id, reloaded.groupId)
        assertFalse(ActionStorage.chatGroups.single().enabled)

        ActionStorage.deleteGroup(group.id)
        assertTrue(ActionStorage.chatGroups.isEmpty())
        assertNull(reloaded.groupId)
    }
}
