package org.blackaddons.core.actions

import com.google.gson.JsonObject
import org.blackaddons.api.util.formatCoordinates
import org.blackaddons.api.util.io.withoutNulls
import org.blackaddons.core.actions.steps.Conditional
import org.blackaddons.core.config.portable.DiffRow
import org.blackaddons.core.config.portable.DiffValue
import org.blackaddons.core.config.portable.ImportPreview
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.config.portable.PortableKind
import java.util.UUID

/**
 * Share string for actions or a whole profile. A profile (`"profile": true`) imports as a new profile. Smaller sets
 * join the selected profile. Ids always change, so pasting twice gives u two actions.
 */
internal object ActionPortable : PortableKind<ActionProfile>("actions") {
    private val tr = PortableImports.tr.scope("actions")
    private val gson = ActionJson.gson()

    private const val EMPTY = "-"

    private val ACTION_LISTS = listOf("chatActions", "waypoints", "shortcuts", "locationActions")

    override val title: String get() = tr("title", "Import actions")
    override val noun: String get() = tr("noun", "actions")

    fun export(content: ActionProfile, asProfile: Boolean): String =
        encode(gson.toJsonTree(content).asJsonObject.apply {
            remove("id")
            if (!asProfile) remove("name")
            /*
             * Importing gives every action a new id. Group ids stay, because they tie actions to their groups inside
             * the string.
             */
            for (list in ACTION_LISTS) getAsJsonArray(list)?.forEach { (it as? JsonObject)?.remove("id") }
        }, asProfile)

    override fun preview(json: JsonObject): ImportPreview<ActionProfile> {
        val content = runCatching { gson.fromJson(json.withoutNulls(), ActionProfile::class.java).adoptLegacy() }.getOrNull()
            ?: return ImportPreview.Failed(tr("err_read", "Those actions could not be read."))
        if (!known(content)) return ImportPreview.Failed(tr("err_newer", "Those actions use steps this version does not have."))

        val rows = rows(content)
        if (rows.isEmpty()) return ImportPreview.Failed(tr("err_empty", "That string has no actions in it."))
        return profilePreview(
            json, content.name, ActionStorage.activeProfile.name, rows,
            asNewProfile = { ActionStorage.addProfile(content.name, fresh(content, existingGroups = emptyList())) },
            addToActive = { add(content) },
        )
    }

    /** Copies through JSON, so the copy shares no live objects with [content]. */
    fun duplicate(content: ActionProfile) {
        add(gson.fromJson(gson.toJsonTree(content), ActionProfile::class.java))
    }

    private fun add(content: ActionProfile): ActionProfile {
        val incoming = fresh(content, ActionStorage.activeProfile.allGroups().toList())
        ActionStorage.chatActions += incoming.chatActions
        ActionStorage.chatGroups += incoming.chatGroups
        ActionStorage.shortcutGroups += incoming.shortcutGroups
        ActionStorage.locationGroups += incoming.locationGroups
        ActionStorage.waypointGroups += incoming.waypointGroups
        ActionStorage.waypoints += incoming.waypoints
        ActionStorage.shortcuts += incoming.shortcuts
        ActionStorage.locationActions += incoming.locationActions
        ActionStorage.save()
        return ActionStorage.activeProfile
    }

    /** Gives fresh ids. An action keeps its group if the group came with it or exists in [existingGroups]. */
    private fun fresh(content: ActionProfile, existingGroups: List<ActionGroup>): ActionProfile {
        val groupIds = content.allGroups().associate { it.id to UUID.randomUUID() }
        fun regroup(id: UUID?) = groupIds[id] ?: id?.takeIf { existingGroups.any { it.id == id } }
        fun MutableList<ActionGroup>.renewed() = mapTo(mutableListOf()) { it.copy(id = groupIds.getValue(it.id)) }
        return content.copy(
            chatGroups = content.chatGroups.renewed(),
            waypointGroups = content.waypointGroups.renewed(),
            shortcutGroups = content.shortcutGroups.renewed(),
            locationGroups = content.locationGroups.renewed(),
            waypoints = content.waypoints.mapTo(mutableListOf()) { it.copy(id = UUID.randomUUID(), groupId = regroup(it.groupId)) },
            chatActions = content.chatActions.mapTo(mutableListOf()) { it.copy(id = UUID.randomUUID(), groupId = regroup(it.groupId)) },
            shortcuts = content.shortcuts.mapTo(mutableListOf()) { it.copy(id = UUID.randomUUID(), groupId = regroup(it.groupId)) },
            locationActions = content.locationActions.mapTo(mutableListOf()) { it.copy(id = UUID.randomUUID(), groupId = regroup(it.groupId)) },
        )
    }

    /** Gson reads unknown enum constants (from newer versions) as null. */
    private fun known(content: ActionProfile): Boolean =
        content.allSteps().all(::known) && content.waypoints.all { (it.shape as WaypointShape?) != null }

    /** False for a step type no installed addon has, or for saved values this version can't read. */
    internal fun known(step: ActionStep): Boolean {
        if (step.type == null) return false
        val condition = (step.params as? Conditional)?.condition
        return !step.lossy &&
            (condition == null || ((condition.type as ConditionType?) != null && (condition.itemScope as ItemScope?) != null))
    }

    private fun rows(content: ActionProfile): List<DiffRow> = buildList {
        (content.chatGroups + content.shortcutGroups + content.locationGroups).forEach { add(row(tr("kind_action_group", "Group"), it.name)) }
        content.chatActions.forEach { add(row(tr("kind_chat", "Chat action"), it.pattern)) }
        content.waypointGroups.forEach { add(row(tr("kind_group", "Waypoint group"), it.name)) }
        content.waypoints.forEach { add(row(tr("kind_waypoint", "Waypoint"), it.name.ifBlank { formatCoordinates(it.x, it.y, it.z) })) }
        content.shortcuts.forEach { add(row(tr("kind_shortcut", "Shortcut"), it.name.ifBlank { it.alias })) }
        content.locationActions.forEach { add(row(tr("kind_location", "Location action"), it.name)) }
    }

    private fun row(kind: String, name: String) =
        DiffRow(kind, DiffValue(EMPTY), DiffValue(name.ifBlank { PortableImports.tr("unnamed", "Unnamed") }))
}
