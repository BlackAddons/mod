package org.blackaddons.core.actions

import org.blackaddons.api.Platform
import org.blackaddons.core.actions.handlers.ShortcutActionHandler
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.actions.steps.TempWaypointStep
import org.blackaddons.core.actions.steps.TextOverlayStep
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.GuardedFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal object ActionStorage {
    private val gson = ActionJson.gson(pretty = true)

    internal val actionsDir get() = Platform.configDir.resolve("actions")
    internal val graphDir get() = actionsDir.resolve("graph")
    private val manifestPath get() = actionsDir.resolve("profiles.json")

    var manifest = ActionProfileManifest()
        private set

    var activeProfile: ActionProfile = ActionProfile(id = "default", name = "Default")
        private set

    private var manifestFile: GuardedFile? = null
    private var profileFileGuard: GuardedFile? = null

    private fun manifestGuard(): GuardedFile =
        manifestFile?.takeIf { it.path == manifestPath } ?: GuardedFile(manifestPath).also { manifestFile = it }

    private fun profileGuard(id: String): GuardedFile? {
        val path = profileFile(id) ?: return null
        return profileFileGuard?.takeIf { it.path == path } ?: GuardedFile(path).also { profileFileGuard = it }
    }

    val chatActions: MutableList<ChatAction> get() = activeProfile.chatActions
    val waypoints: MutableList<WaypointDefinition> get() = activeProfile.waypoints
    val waypointGroups: MutableList<ActionGroup> get() = activeProfile.waypointGroups
    val shortcuts: MutableList<ShortcutAction> get() = activeProfile.shortcuts
    val locationActions: MutableList<LocationAction> get() = activeProfile.locationActions
    val chatGroups: MutableList<ActionGroup> get() = activeProfile.chatGroups
    val shortcutGroups: MutableList<ActionGroup> get() = activeProfile.shortcutGroups
    val locationGroups: MutableList<ActionGroup> get() = activeProfile.locationGroups

    fun load() {
        manifestFile = GuardedFile(manifestPath)
        val guard = manifestGuard()
        val manifestText = guard.read()
        if (manifestText == null) {
            if (guard.writable) {
                initDefaultProfile(persist = true)
                return
            }
            profileGuard(activeProfile.id)?.refuseWrites("its manifest could not be read")
            return
        }

        runCatching {
            val loadedManifest: ActionProfileManifest? = gson.fromJson(manifestText, ActionProfileManifest::class.java)
            val validManifest = loadedManifest?.also { loaded ->
                val seenIds = mutableSetOf<String>()
                if (loaded.profiles.removeIf { header -> !isValidProfileId(header.id) || !seenIds.add(header.id) }) {
                    Log.main.warn("Ignoring invalid or duplicate action profile IDs in {}", manifestPath)
                }
            }
            if (validManifest != null && validManifest.profiles.isNotEmpty()) {
                manifest = validManifest
                var targetId = manifest.activeProfileId
                if (manifest.profiles.none { it.id == targetId }) {
                    targetId = manifest.profiles.first().id
                    manifest.activeProfileId = targetId
                }
                loadProfileFile(targetId)
            } else {
                initDefaultProfile(persist = false)
            }
        }.onFailure {
            Log.main.error("Failed to load action storage", it)
            guard.refuseWrites("it could not be parsed")
            profileGuard(activeProfile.id)?.refuseWrites("its manifest could not be parsed")
        }
    }

    private fun initDefaultProfile(persist: Boolean) {
        val defaultProfile = withDefaults(ActionProfile(id = "default", name = "Default"))

        activeProfile = defaultProfile
        manifest = ActionProfileManifest(
            activeProfileId = "default",
            profiles = mutableListOf(ActionProfileHeader(id = "default", name = "Default")),
        )

        if (persist) save()
    }

    private fun loadProfileFile(id: String) {
        profileFileGuard = null
        val guard = profileGuard(id) ?: return
        val path = guard.path

        val text = guard.read()
        if (text != null) {
            val loaded = runCatching { gson.fromJson(text, ActionProfile::class.java).adoptLegacy() }
                .onFailure {
                    Log.main.error("Failed to read action profile {}", path, it)
                    guard.refuseWrites("it could not be parsed")
                }
                .getOrNull()
            if (loaded?.id == id) {
                activeProfile = loaded
                return
            }
            if (loaded != null) {
                Log.main.warn("Ignoring action profile {} because its stored ID does not match", path)
                guard.refuseWrites("it holds a different profile id")
            }
        }
        val header = manifest.profiles.firstOrNull { it.id == id }
        activeProfile = ActionProfile(id = id, name = header?.name ?: "Default")
    }

    fun save() {
        saveActiveProfile()
        saveManifest()
        ShortcutActionHandler.syncAliases()
    }

    fun inActiveGroup(groups: List<ActionGroup>, item: Groupable): Boolean =
        item.groupId?.let { id -> groups.firstOrNull { it.id == id }?.isActive() } ?: true

    /** The members stay and become ungrouped. */
    fun deleteGroup(groupId: UUID) {
        val profile = activeProfile
        for (groups in listOf(profile.chatGroups, profile.waypointGroups, profile.shortcutGroups, profile.locationGroups)) {
            groups.removeIf { it.id == groupId }
        }
        profile.allGroupables().forEach { if (it.groupId == groupId) it.groupId = null }
        save()
    }

    private fun saveActiveProfile() {
        val guard = profileGuard(activeProfile.id) ?: run {
            Log.main.warn("Refusing to save action profile with invalid ID '{}'", activeProfile.id)
            return
        }
        guard.write(gson.toJson(activeProfile))
    }

    private fun saveManifest() {
        manifestGuard().write(gson.toJson(manifest))
    }

    fun selectProfile(id: String) {
        if (activeProfile.id == id) return
        saveActiveProfile()

        val header = manifest.profiles.firstOrNull { it.id == id } ?: return
        if (profileFile(header.id) == null) return
        manifest.activeProfileId = header.id
        loadProfileFile(header.id)
        saveManifest()
        ShortcutActionHandler.syncAliases()
    }

    fun createProfile(name: String, copyCurrent: Boolean = false): ActionProfile = addProfile(
        name,
        if (copyCurrent) gson.fromJson(gson.toJson(activeProfile), ActionProfile::class.java)
        else withDefaults(ActionProfile()),
    )

    /** Also selects it. */
    fun addProfile(name: String, content: ActionProfile): ActionProfile {
        saveActiveProfile()
        val newId = UUID.randomUUID().toString()
        val trimmedName = name.trim().ifEmpty { "New Profile" }
        val newProfile = content.copy(id = newId, name = trimmedName)

        manifest.profiles.add(ActionProfileHeader(id = newId, name = trimmedName))
        manifest.activeProfileId = newId
        activeProfile = newProfile

        save()
        return newProfile
    }

    fun renameProfile(id: String, newName: String) {
        val trimmed = newName.trim().ifEmpty { "Unnamed Profile" }
        val file = profileFile(id) ?: return
        val header = manifest.profiles.firstOrNull { it.id == id } ?: return
        header.name = trimmed

        if (activeProfile.id == id) {
            activeProfile.name = trimmed
            saveActiveProfile()
        } else {
            val guard = GuardedFile(file)
            guard.read()?.let { text ->
                runCatching { gson.fromJson(text, ActionProfile::class.java).adoptLegacy() }
                    .onFailure {
                        Log.main.error("Failed to read action profile {}", file, it)
                        guard.refuseWrites("it could not be parsed")
                    }
                    .getOrNull()
                    ?.let { loaded ->
                        loaded.name = trimmed
                        guard.write(gson.toJson(loaded))
                    }
            }
        }
        saveManifest()
    }

    fun deleteProfile(id: String): Boolean {
        if (manifest.profiles.size <= 1) return false
        val file = profileFile(id) ?: return false
        val header = manifest.profiles.firstOrNull { it.id == id } ?: return false

        manifest.profiles.remove(header)
        runCatching { Files.deleteIfExists(file) }
        runCatching { Files.list(graphDir).use { boards -> boards.forEach { Files.deleteIfExists(it.resolve("$id.json")) } } }

        if (activeProfile.id == id) {
            val nextHeader = manifest.profiles.last()
            manifest.activeProfileId = nextHeader.id
            loadProfileFile(nextHeader.id)
        }

        saveManifest()
        ShortcutActionHandler.syncAliases()
        return true
    }

    private fun profileFile(id: String): Path? =
        if (isValidProfileId(id)) actionsDir.resolve("$id.json") else null

    // Guards the file name. Don't use Uuids.parse, it'll fuck up the file name.
    internal fun isValidProfileId(id: String): Boolean =
        id == "default" || runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    private fun withDefaults(profile: ActionProfile): ActionProfile = profile.apply {
        chatActions += defaultChatActions()
        val posMsg = ActionGroup(
            name = "Pos Msg",
            enabled = false,
            areaFilters = mutableListOf("Catacombs"),
            inDungeonFilter = true,
            inBossFilter = true,
        )
        waypointGroups += posMsg
        waypoints += defaultPosMessages(posMsg.id)
    }

    private fun defaultPosMessages(groupId: UUID): List<WaypointDefinition> = listOf(
        posMessage(groupId, "at ss", "at ss", 108, 120, 94, 2.0),
        posMessage(groupId, "ee2", "at ee2", 60, 132, 139, 1.0),
        posMessage(groupId, "ee3", "at ee3", 2, 109, 105, 1.0),
        posMessage(groupId, "at core", "at core", 54, 115, 52, 2.0),
        posMessage(groupId, "at mid", "at mid", 54, 65, 76, 8.0),
    )

    private fun posMessage(groupId: UUID, label: String, message: String, x: Int, y: Int, z: Int, radius: Double) =
        WaypointDefinition(
            groupId = groupId,
            name = label,
            x = x + 0.5,
            y = y.toDouble(),
            z = z + 0.5,
            colorRgb = 0xFFFFFF,
            radius = radius,
            actions = mutableListOf(
                WaypointAction(
                    triggeredBy = TriggeredBy.ANYONE,
                    actions = mutableListOf(
                        ActionStep.of(TextOverlayStep, TextOverlayStep.Params(message = "{player} $message", durationSeconds = 1f), triggeredBy = TriggeredBy.ANYONE),
                        // Only your own position counts, or the party chat would echo every teammate that walks in.
                        ActionStep.of(SendMessageStep, SendMessageStep.Params(message = "/pc $message"), enabled = false),
                    ),
                ),
            ),
        )

    private fun defaultChatActions(): List<ChatAction> = listOf(
        ChatAction(
            pattern = "(?s).*?(?:\\[.*?\\] )?([A-Za-z0-9_]+) has invited you to join their party!.*You have 60 seconds to accept.*",
            isRegex = true,
            soundId = "minecraft:entity.cat.ambient",
            volume = 1.0f,
            pitch = 1.0f,
            enabled = false,
            title = "&cParty Invite!",
            subtitle = "&6From: &e{1}",
            durationSeconds = 2.0f,
        ),
        ChatAction(
            pattern = "Party Finder > ([A-Za-z0-9_]+) joined the dungeon group! \\((.*)\\)",
            isRegex = true,
            soundId = "minecraft:entity.experience_orb.pickup",
            volume = 1.0f,
            pitch = 1.0f,
            enabled = false,
            title = "&a{1} Joined!",
            subtitle = "&7Class: &b{2}",
            durationSeconds = 3.0f,
        ),
        ChatAction(
            pattern = TEMP_WAYPOINT_PATTERN,
            isRegex = true,
            ignoreOwnMessages = true,
            enabled = false,
            actions = mutableListOf(
                ActionStep.of(TempWaypointStep, TempWaypointStep.Params(message = "{1}{5}", waypointCoords = "{2} {3} {4}")),
            ),
        ),
    )

    /** {1} sender, {2} {3} {4} x y z, {5} is the note from feesh formatting, prob should change if something else appears. */
    internal const val TEMP_WAYPOINT_PATTERN =
        """(\w{1,16})(?: \[[^\]]+])?(?: [^\w\s])?: .*?\bx:? (-?\d+(?:\.\d+)?),? y:? (-?\d+(?:\.\d+)?),? z:? (-?\d+(?:\.\d+)?)( \| [^|]*[^|\s])?"""

    fun openFolder() = Platform.desktop.openPath(actionsDir)
}
