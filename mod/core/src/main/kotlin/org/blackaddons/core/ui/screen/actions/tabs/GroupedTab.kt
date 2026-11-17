package org.blackaddons.core.ui.screen.actions.tabs
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Chevron
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.moveItem
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionProfile
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.Groupable
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.ActionMenu
import org.blackaddons.core.ui.screen.actions.ActionRow
import org.blackaddons.core.ui.screen.actions.ActionRowButtons
import org.blackaddons.core.ui.screen.actions.ActionsPage
import org.blackaddons.core.ui.screen.actions.RowButton
import org.blackaddons.core.ui.screen.actions.actionTr
import java.awt.Color

internal sealed class GroupedRow<out T> {
    data class GroupRow(val group: ActionGroup, val count: Int) : GroupedRow<Nothing>()
    data class ItemRow<T>(val item: T, val inGroup: Boolean) : GroupedRow<T>()
}

/** Tab of collapsible groups. Dragging an action under a row moves it to that row's group. Rows rebuild each frame. */
internal abstract class GroupedTab<T : Groupable>(
    protected val widgets: WidgetHost,
    private val page: ActionsPage,
) : ActionsTab {

    protected abstract val items: MutableList<T>

    private val groups: MutableList<ActionGroup> get() = page.groups(ActionStorage.activeProfile)

    /** [rx]/[rw] are already indented inside a group. */
    protected abstract fun drawItem(item: T, index: Int, rx: Float, ry: Float, rw: Float)

    protected abstract fun content(group: ActionGroup, members: MutableList<T>): ActionProfile

    override val list = ScrollableList<GroupedRow<T>>().apply {
        rowHeight = Theme.FEATURE_ROW_HEIGHT
        drawRow = { row, idx, rx, ry, rw ->
            when (row) {
                is GroupedRow.GroupRow -> drawGroupRow(row.group, row.count, rx, ry, rw)
                is GroupedRow.ItemRow -> {
                    val indent = if (row.inGroup) INDENT else 0f
                    drawItem(row.item, idx, rx + indent, ry, rw - indent)
                }
            }
        }
        key = { row ->
            when (row) {
                is GroupedRow.GroupRow -> row.group.id
                is GroupedRow.ItemRow -> row.item.id
            }
        }
        canDrag = { items.getOrNull(it) is GroupedRow.ItemRow }
        onReorder = ::drop
    }

    override fun render(x: Float, y: Float, w: Float, h: Float) {
        list.items = buildRows()
        list.render(x, y, w, h)
    }

    override fun addGroup() {
        ActionEditors.createGroup(groups)
    }

    private fun buildRows(): List<GroupedRow<T>> = buildList {
        val groups = groups
        for (group in groups) {
            val children = items.filter { it.groupId == group.id }
            add(GroupedRow.GroupRow(group, children.size))
            if (!group.collapsed) children.forEach { add(GroupedRow.ItemRow(it, inGroup = true)) }
        }
        items.filter { item -> item.groupId == null || groups.none { it.id == item.groupId } }
            .forEach { add(GroupedRow.ItemRow(it, inGroup = false)) }
    }

    private fun drawGroupRow(group: ActionGroup, count: Int, rx: Float, ry: Float, rw: Float) {
        val rowH = list.rowHeight
        val resting = if (group.enabled) Theme.SURFACE_ACTIVE.withAlpha(220) else Theme.SURFACE.withAlpha(140)
        widgets.clickable(rx, ry, rw, rowH) {
            group.collapsed = !group.collapsed
            ActionStorage.save()
        }
        ActionRow.background(rx, ry, rw, rowH, group.enabled, resting)

        val chevronX = rx + Theme.PADDING + 4f
        Chevron.draw(
            chevronX, ry + rowH / 2f, 3.5f, 1.4f,
            if (group.collapsed) 0f else 1f,
            if (group.enabled) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED,
        )

        val name = group.name.ifBlank { actionTr("group.unnamed", "Unnamed Group") }
        val delete = { ActionStorage.deleteGroup(group.id) }
        widgets.clickable(rx, ry, rw, rowH, Keys.MOUSE_RIGHT) {
            val members = items.filterTo(mutableListOf()) { it.groupId == group.id }
            ActionMenu.row(name, content(group, members), edit = { ActionEditors.editGroup(group) }, steps = null, delete)
        }
        val pillLimit = ActionRowButtons.stripLeft(rx, rw, withSteps = false) - 10f
        var x = ActionRow.title("$name ($count)", chevronX + 10f, ry, rowH, GROUP_TITLE_W, group.enabled, Fonts.bold)
        x = ActionRow.pill(areaLabel(group.areaFilters.size, group.areaFilters.firstOrNull()), x, ry, rowH, AREA_PILL, pillLimit)
        x = ActionRow.pill(group.inDungeonFilter?.let { actionTr(if (it) "dungeon" else "no_dungeon", if (it) "Dungeon" else "No Dungeon") }, x, ry, rowH, DUNGEON_PILL, pillLimit)
        x = ActionRow.pill(group.floorFilter?.label, x, ry, rowH, Theme.ACCENT.withAlpha(140), pillLimit)
        x = ActionRow.pill(group.inBossFilter?.let { actionTr(if (it) "boss" else "no_boss", if (it) "Boss" else "No Boss") }, x, ry, rowH, BOSS_PILL, pillLimit)
        ActionRow.pill(group.phaseFilter?.let { "P$it" }, x, ry, rowH, PHASE_PILL, pillLimit)

        ActionRowButtons.draw(widgets, rx, ry, rw, group.enabled, withSteps = false) { button ->
            when (button) {
                RowButton.DELETE -> confirmDelete(rx, ry, rw, withSteps = false, delete = delete)
                RowButton.EDIT -> ActionEditors.editGroup(group)
                RowButton.TOGGLE -> {
                    group.enabled = !group.enabled
                    ActionStorage.save()
                }
                RowButton.STEPS -> Unit
            }
        }
    }

    /** The row above decides the group and the storage position. */
    private fun drop(from: Int, to: Int) {
        val rows = list.items.toMutableList()
        val item = (rows.getOrNull(from) as? GroupedRow.ItemRow)?.item ?: return
        rows.moveItem(from, to)
        val above = rows.getOrNull(to - 1)
        val group = when (above) {
            is GroupedRow.GroupRow -> above.group
            is GroupedRow.ItemRow -> groups.firstOrNull { it.id == above.item.groupId }
            null -> null
        }
        item.groupId = group?.id
        group?.collapsed = false

        /* After the action shown above it in the group, otherwise before the one below. */
        val storage = items
        storage.remove(item)
        val previous = (above as? GroupedRow.ItemRow)?.item?.takeIf { it.groupId == item.groupId }
        val next = (rows.getOrNull(to + 1) as? GroupedRow.ItemRow)?.item?.takeIf { it.groupId == item.groupId }
        val at = when {
            previous != null -> storage.indexOf(previous) + 1
            next != null -> storage.indexOf(next)
            else -> storage.size
        }
        storage.add(at.coerceIn(0, storage.size), item)
        ActionStorage.save()
    }

    private fun areaLabel(count: Int, first: String?): String? = when (count) {
        0 -> null
        1 -> first
        else -> actionTr("areas", "%d Areas", count)
    }

    private companion object {
        const val INDENT = 16f
        const val GROUP_TITLE_W = 160f

        val AREA_PILL = Color(120, 60, 160, 180)
        val DUNGEON_PILL = Color(0, 100, 40, 180)
        val BOSS_PILL = Color(160, 40, 40, 180)
        val PHASE_PILL = Color(40, 80, 160, 180)
    }
}
