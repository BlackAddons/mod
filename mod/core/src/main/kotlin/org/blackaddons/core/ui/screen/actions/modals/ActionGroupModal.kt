package org.blackaddons.core.ui.screen.actions.modals
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.log.Log
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.LocationFilterFields

/** Group name and location filters. A new group joins [into] on save. */
internal class ActionGroupModal(
    private val group: ActionGroup,
    private val into: MutableList<ActionGroup>? = null,
    private val onSaved: () -> Unit = {},
) : ModalPanel(
    when {
        into == null -> actionTr("group.edit", "Edit Group")
        into === ActionStorage.waypointGroups -> actionTr("group.new", "New Waypoint Group")
        else -> actionTr("group.new_name", "New Group")
    },
) {

    override val panelWidth: Float
        get() = fitWidth(440f, min = 320f)

    override val panelHeight: Float
        get() = fitHeight(400f, min = 320f, margin = 30f)

    private val nameInput = widgets.add(TextInput(placeholder = actionTr("group.name_placeholder", "Group Name")).apply {
        text = group.name
    })

    private val location = LocationFilterFields().apply {
        attachTo(widgets)
        readFrom(group)
    }

    private val captureLocationBtn = widgets.add(Button(actionTr("group.use_current_location", "Use Current Location")) {
        if (Platform.player.isInGame) runCatching { location.readFromCurrentLocation() }
            .onFailure { Log.main.error("Could not read the current location", it) }
    })

    private val saveBtn = widgets.add(Button(actionTr("save", "Save")) {
        saveAndClose()
        UiManager.pop()
    }).apply { accent = true }

    private fun saveAndClose() {
        group.name = nameInput.text.trim().ifEmpty { actionTr("group.unnamed", "Unnamed Group") }
        location.writeTo(group)

        if (into != null && into.none { it.id == group.id }) into.add(group)
        ActionStorage.save()
        onSaved()
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val form = column(spacing = Theme.PADDING, width = contentW) {
            text(actionTr("group.name", "Group Name:"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { cx, cy -> nameInput.render(cx, cy, contentW, 22f, mouseX, mouseY) }

            location.section(this, contentW, mouseX, mouseY)

            cell(contentW, 24f) { cx, cy -> captureLocationBtn.render(cx, cy, contentW, 24f, mouseX, mouseY) }
        }

        form.draw(contentX, contentY)
        location.renderOverlays(mouseX, mouseY)

        val saveY = contentY + contentH - 26f
        saveBtn.render(contentX, saveY, contentW, 26f, mouseX, mouseY)
    }

    override fun onEscape() {
        saveAndClose()
    }
}
