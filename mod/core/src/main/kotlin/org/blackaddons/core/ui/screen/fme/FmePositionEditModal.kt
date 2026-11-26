package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.features.impl.visual.fme.*

internal class FmePositionEditModal private constructor(
    title: String,
    private val profileId: String,
    private val scope: FmeScope,
    private val scopeKey: String,
    initial: Triple<Int, Int, Int>?,
    private val save: (Int, Int, Int) -> SaveResult,
    private val onSaved: (Int, Int, Int) -> Unit,
) : ModalPanel(title) {

    constructor(
        key: FmePositionKey,
        initial: Triple<Int, Int, Int> = Triple(key.x, key.y, key.z),
        onMoved: (FmePositionKey) -> Unit,
    ) : this(
        title = tr("edit_position", "Edit position"),
        profileId = key.profileId,
        scope = key.scope,
        scopeKey = key.scopeKey,
        initial = initial,
        save = { x, y, z ->
            when (Fme.movePosition(key, x, y, z)) {
                FmePositionMoveResult.MOVED -> SaveResult.SAVED
                FmePositionMoveResult.UNCHANGED -> SaveResult.UNCHANGED
                FmePositionMoveResult.OCCUPIED -> SaveResult.OCCUPIED
                FmePositionMoveResult.MISSING -> SaveResult.MISSING
            }
        },
        onSaved = { x, y, z -> onMoved(key.copy(x = x, y = y, z = z)) },
    )

    constructor(group: FmeManagedGroup, onAdded: (FmePositionKey) -> Unit) : this(
        title = tr("add_position", "Add position"),
        profileId = group.ruleKeys.first().profileId,
        scope = group.scope,
        scopeKey = group.scopeKey,
        initial = null,
        save = { x, y, z ->
            when (Fme.addPosition(group, x, y, z)) {
                FmePositionAddResult.ADDED -> SaveResult.SAVED
                FmePositionAddResult.OCCUPIED -> SaveResult.OCCUPIED
                FmePositionAddResult.MISSING -> SaveResult.MISSING
            }
        },
        onSaved = { x, y, z ->
            onAdded(FmePositionKey(group.ruleKeys.first().profileId, group.scope, group.scopeKey, x, y, z))
        },
    )

    override val panelWidth: Float get() = fitWidth(340f, min = 280f)
    override val panelHeight: Float get() = fitHeight(170f, min = 150f)

    private val xInput = coordinateInput(initial?.first)
    private val yInput = coordinateInput(initial?.second)
    private val zInput = coordinateInput(initial?.third)
    private val pasteButton = widgets.add(Button(tr("paste_coordinates", "Paste coordinates")) { pasteCoordinates() })
    private val cancelButton = widgets.add(Button(tr("cancel", "Cancel")) { UiManager.pop() })
    private val applyButton = widgets.add(Button(tr("apply_position", "Apply")) { applyPosition() }.apply { accent = true })

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val coordinateLabel = if (scope == FmeScope.DUNGEON) {
            tr("local_coordinates", "Local coordinates")
        } else {
            tr("world_coordinates", "World coordinates")
        }
        val layout = column(spacing = Theme.PADDING_SMALL, width = contentW, height = contentH) {
            text(coordinateLabel, TextStyle.SMALL_MUTED_MEDIUM)
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = contentW) {
                coordinateField("X", xInput, mouseX, mouseY)
                coordinateField("Y", yInput, mouseX, mouseY)
                coordinateField("Z", zInput, mouseX, mouseY)
            }
            text(previewText(), TextStyle.SMALL_SECONDARY)
            flexSpacer()
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = contentW) {
                cell(PASTE_W, BUTTON_H) { x, y -> pasteButton.render(x, y, PASTE_W, BUTTON_H, mouseX, mouseY) }
                flexSpacer()
                cell(BUTTON_W, BUTTON_H) { x, y -> cancelButton.render(x, y, BUTTON_W, BUTTON_H, mouseX, mouseY) }
                cell(BUTTON_W, BUTTON_H) { x, y -> applyButton.render(x, y, BUTTON_W, BUTTON_H, mouseX, mouseY) }
            }
        }
        layout.draw(contentX, contentY)
    }

    private fun org.blackaddons.api.ui.layout.LayoutBuilder.coordinateField(
        label: String,
        input: TextInput,
        mouseX: Float,
        mouseY: Float,
    ) {
        val fieldW = (contentW - Theme.PADDING_SMALL * 2f) / 3f
        cell(fieldW, 38f) { x, y ->
            Render.text(label, x, y, TextStyle.SMALL_MUTED_MEDIUM)
            input.render(x, y + 14f, fieldW, BUTTON_H, mouseX, mouseY)
        }
    }

    private fun coordinateInput(value: Int?): TextInput = widgets.add(
        TextInput(maxLength = 11, charFilter = { it.isDigit() || it == '-' }).apply {
            text = value?.toString().orEmpty()
            onEnter = ::applyPosition
        },
    )

    private fun previewText(): String {
        val coordinates = coordinates() ?: return tr("position_invalid", "Enter valid whole-number coordinates.")
        if (scope != FmeScope.DUNGEON) {
            return tr("position_preview", "Position: %d, %d, %d", coordinates.first, coordinates.second, coordinates.third)
        }
        val key = FmePositionKey(profileId, scope, scopeKey, coordinates.first, coordinates.second, coordinates.third)
        val world = Fme.worldPosition(key)
            ?: return tr("world_preview_unavailable", "World preview unavailable outside this room.")
        return tr("world_preview", "World: %d, %d, %d", world.x, world.y, world.z)
    }

    private fun pasteCoordinates() {
        val coordinates = parseFmeCoordinates(Platform.clipboard.paste())
        if (coordinates == null) {
            Toasts.show(
                tr("coordinates_invalid", "Clipboard does not contain three whole-number coordinates."),
                kind = ToastKind.ERROR,
            )
            return
        }
        xInput.text = coordinates.first.toString()
        yInput.text = coordinates.second.toString()
        zInput.text = coordinates.third.toString()
    }

    private fun applyPosition() {
        val coordinates = coordinates()
        xInput.invalid = xInput.text.toIntOrNull() == null
        yInput.invalid = yInput.text.toIntOrNull() == null
        zInput.invalid = zInput.text.toIntOrNull() == null
        if (coordinates == null) return
        when (save(coordinates.first, coordinates.second, coordinates.third)) {
            SaveResult.SAVED -> {
                onSaved(coordinates.first, coordinates.second, coordinates.third)
                UiManager.pop()
            }
            SaveResult.UNCHANGED -> UiManager.pop()
            SaveResult.OCCUPIED -> Toasts.show(
                tr("position_occupied", "That position already has an FME edit."),
                kind = ToastKind.ERROR,
            )
            SaveResult.MISSING -> {
                Toasts.show(tr("position_missing", "That position edit no longer exists."), kind = ToastKind.ERROR)
                UiManager.pop()
            }
        }
    }

    private fun coordinates(): Triple<Int, Int, Int>? = Triple(
        xInput.text.toIntOrNull() ?: return null,
        yInput.text.toIntOrNull() ?: return null,
        zInput.text.toIntOrNull() ?: return null,
    )

    private enum class SaveResult { SAVED, UNCHANGED, OCCUPIED, MISSING }

    private companion object {
        val tr = I18n.scope("blackaddons.fme")

        const val BUTTON_H = 22f
        const val BUTTON_W = 72f
        const val PASTE_W = 124f
    }
}

internal fun parseFmeCoordinates(text: String): Triple<Int, Int, Int>? {
    val values = Regex("-?\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.take(4).toList()
    return if (values.size == 3) Triple(values[0], values[1], values[2]) else null
}
