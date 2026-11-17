package org.blackaddons.core.ui.screen.profile

import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.ui.screen.profile.ProfileMenu.tr

internal class ProfilesModal(
    private val store: ProfileStore,
    private val changed: () -> Unit,
) : ModalPanel(store.title) {

    override val panelWidth: Float get() = fitWidth(440f, min = 300f)
    override val panelHeight: Float get() = fitHeight(286f, min = 240f)

    private val profileDropdown = widgets.add(Dropdown())
    private val nameInput = widgets.add(TextInput(placeholder = tr("name", "Profile name"))).apply {
        text = store.active.name
    }
    private val newButton = widgets.add(Button(tr("new", "New Profile")) {
        saveName()
        store.create(tr("new_name", "New Profile"), copyCurrent = false)
        sync()
    })
    private val duplicateButton = widgets.add(Button(tr("duplicate", "Duplicate Profile")) {
        saveName()
        store.create(tr("copy_name", "%s (Copy)", store.active.name), copyCurrent = true)
        sync()
    })
    private val deleteButton = widgets.add(Button(tr("delete", "Delete Profile")) {
        if (store.profiles.size > 1) {
            UiManager.confirmPopover {
                store.delete(store.active.id)
                sync()
            }
        }
    }.apply { danger = true })
    private val openFolderButton = widgets.add(Button(tr("open_folder", "Open Folder")) { store.openFolder() })
    private val doneButton = widgets.add(Button(I18n.trOr("blackaddons.list.done", "Done")) {
        saveName()
        UiManager.pop()
        changed()
    }.apply { accent = true })

    init {
        syncDropdown()
        profileDropdown.onSelect = { index ->
            saveName()
            store.profiles.getOrNull(index)?.let { store.select(it.id) }
            sync()
        }
    }

    private fun sync() {
        nameInput.text = store.active.name
        syncDropdown()
        changed()
    }

    private fun syncDropdown() {
        val profiles = store.profiles
        profileDropdown.items = profiles.map { Dropdown.Item(it.name) }
        profileDropdown.selectedIndex = profiles.indexOfFirst { it.id == store.active.id }.coerceAtLeast(0)
    }

    private fun saveName() {
        val name = nameInput.text.trim()
        if (name.isNotEmpty() && name != store.active.name) store.rename(store.active.id, name)
    }

    override fun onEscape() {
        saveName()
        changed()
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        deleteButton.enabled = store.profiles.size > 1
        val layout = column(spacing = Theme.PADDING_SMALL, width = contentW, height = contentH) {
            text(tr("select", "Profile"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { x, y ->
                syncDropdown()
                profileDropdown.render(x, y, contentW, 22f, mouseX, mouseY)
            }
            text(tr("name", "Profile name"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { x, y -> nameInput.render(x, y, contentW, 22f, mouseX, mouseY) }
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = contentW) {
                val buttonW = (contentW - Theme.PADDING_SMALL * 2f) / 3f
                cell(buttonW, 24f) { x, y -> newButton.render(x, y, buttonW, 24f, mouseX, mouseY) }
                cell(buttonW, 24f) { x, y -> duplicateButton.render(x, y, buttonW, 24f, mouseX, mouseY) }
                cell(buttonW, 24f) { x, y -> deleteButton.render(x, y, buttonW, 24f, mouseX, mouseY) }
            }
            flexSpacer()
            row(spacing = Theme.PADDING, align = Align.CENTER, width = contentW) {
                cell(110f, 24f) { x, y -> openFolderButton.render(x, y, 110f, 24f, mouseX, mouseY) }
                flexSpacer()
                cell(90f, 24f) { x, y -> doneButton.render(x, y, 90f, 24f, mouseX, mouseY) }
            }
        }
        layout.draw(contentX, contentY)
        profileDropdown.renderOverlay(mouseX, mouseY)
    }
}
