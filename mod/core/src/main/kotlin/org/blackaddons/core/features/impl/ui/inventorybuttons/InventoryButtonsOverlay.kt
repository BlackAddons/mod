package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.gui.ContainerOverlay
import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.input.Keys
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.I18n
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ShortcutAction
import org.blackaddons.core.actions.execution.ActionExecutor

private val tr = I18n.scope("blackaddons.inventory_buttons")

internal object InventoryButtonsOverlay : ContainerOverlay {

    var editMode = false
        private set

    // Edit mode starts once the inventory actually shows, which can be a tick after the request.
    private var editPending = false

    private var drawnAtMs = 0L
    private val live: Boolean get() = System.currentTimeMillis() - drawnAtMs < LIVE_MS

    private var hovered: InventoryButton? = null
    private val clock = FrameClock()

    // Last frame's layout, used for clicks between frames.
    var placed: List<Pair<InventoryButton, GuiBox>> = emptyList()
        private set

    var pencilBox: GuiBox? = null
        private set

    override fun appliesTo(title: String): Boolean = InventoryButtons.isRunning &&
        (editMode || editPending || !InventoryButtons.skyblockOnly.value || LocationUtils.inSkyblock)

    override fun suppressesVanilla(title: String): Boolean = false

    override fun exclusionZones(metrics: ContainerMetrics): List<GuiBox> {
        if (metrics.replaced) return emptyList()
        return layout(metrics).map { it.second } + pencilOf(metrics)
    }

    override fun renderUi(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        val dt = clock.tick().toFloat()
        if (metrics.replaced) {
            placed = emptyList()
            pencilBox = null
            return
        }
        drawnAtMs = System.currentTimeMillis()
        if (editPending && Platform.container.isPlayerInventoryOpen()) {
            editPending = false
            editMode = true
        }
        if (editMode) InventoryButtonEditor.followPointer(metrics, mouseX, mouseY)
        placed = layout(metrics)
        val pencil = pencilOf(metrics).also { pencilBox = it }
        hovered = if (UiManager.isOpen) null else buttonAt(mouseX, mouseY)

        if (editMode) InventoryButtonEditor.drawBackdrop(metrics)
        val dropTarget = InventoryButtonEditor.dropTarget(hovered)
        for ((button, box) in placed) {
            ButtonPainter.drawButton(button, box, hovered = button === hovered && !editMode, dropTarget = button === dropTarget)
        }
        val pencilHovered = pencil.has(mouseX, mouseY) && !UiManager.isOpen
        ButtonPainter.drawPencil(pencil, pencilHovered, editMode)
        if (editMode) InventoryButtonEditor.drawChrome(metrics, hovered, dt, mouseX, mouseY)

        if (UiManager.isOpen) return
        if (pencilHovered) {
            val action = if (editMode) tr("pencil.done", "Done editing") else tr("pencil.edit", "Edit buttons")
            Tooltip.show("$action\n" + tr("pencil.profiles", "Right-click: profiles"))
        } else if (!editMode && InventoryButtons.hoverTooltip.value) {
            hovered?.let { Tooltip.show(describe(it)) }
        }
    }

    override fun render(ctx: Any, metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {}

    override fun mouseClicked(metrics: ContainerMetrics, mouse: MouseButtonInput): Boolean {
        if (metrics.replaced) return false
        val mx = mouse.x.toInt()
        val my = mouse.y.toInt()
        if (editMode) {
            InventoryButtonEditor.click(metrics, mouse, mx, my)
            // Slots ignore clicks while editing.
            return true
        }
        if (pencilBox?.has(mx, my) == true) {
            if (mouse.isLeft) editMode = true
            if (mouse.isRight) InventoryButtonMenus.forPencil(mouse)
            return true
        }
        val button = buttonAt(mx, my) ?: return false
        if (mouse.isLeft) run(button)
        // Always consumed: vanilla treats a click outside the GUI as dropping the carried item.
        return true
    }

    override fun mouseDragged(metrics: ContainerMetrics, drag: MouseDragInput): Boolean = editMode

    override fun mouseReleased(mouse: MouseButtonInput) {
        if (editMode && InventoryButtonEditor.release(mouse)) exitEdit()
    }

    override fun mouseScrolled(metrics: ContainerMetrics, wheel: MouseScrollInput): Boolean =
        editMode && InventoryButtonEditor.scroll(metrics, wheel)

    override fun keyPressed(metrics: ContainerMetrics, key: KeyInput): Boolean {
        if (!editMode) return false
        if (key.code == Keys.ESCAPE) {
            exitEdit()
            return true
        }
        return InventoryButtonEditor.key(metrics, key)
    }

    fun toggleEdit() {
        when {
            editMode -> exitEdit()
            live -> editMode = true
            else -> requestEdit()
        }
    }

    fun requestEdit() {
        if (live && Platform.container.isPlayerInventoryOpen()) {
            editMode = true
            return
        }
        editPending = true
        // A tick later, or a command typed in chat opens it just before the chat closes and takes it down too.
        Platform.scheduler.schedule(0) { Platform.container.openPlayerInventory() }
    }

    // Inside a screen, only where the buttons are and while none of our popups has the keys.
    val keybindAllowed: Boolean
        get() = !Platform.screenHost.anyScreenOpen || live && !UiManager.isOpen && !Platform.screenHost.chatOpen

    // Edit mode ends when the screen closes.
    fun tick() {
        if (editMode && !Platform.screenHost.anyScreenOpen) exitEdit()
    }

    fun reset() {
        if (editMode) InventoryButtonStore.save()
        editMode = false
        editPending = false
        InventoryButtonEditor.reset()
        InventoryButtonEditor.clearHistory()
        ButtonPainter.clearPops()
    }

    private fun exitEdit() {
        InventoryButtonStore.save()
        editMode = false
        InventoryButtonEditor.reset()
    }

    fun buttonAt(x: Int, y: Int): InventoryButton? = placed.lastOrNull { it.second.has(x, y) }?.first

    fun boxOf(button: InventoryButton): GuiBox? =
        if (button === InventoryButtonStore.pencil) pencilBox else placed.firstOrNull { it.first === button }?.second

    private fun pencilOf(metrics: ContainerMetrics): GuiBox =
        placeButton(InventoryButtonStore.pencil, metrics.gui, metrics.width, metrics.height)

    private fun layout(metrics: ContainerMetrics): List<Pair<InventoryButton, GuiBox>> {
        val title = Platform.container.title().orEmpty()
        val inventory = Platform.container.isPlayerInventoryOpen()
        return InventoryButtonStore.buttons
            .filter { shows(it, title, inventory) }
            .map { it to placeButton(it, metrics.gui, metrics.width, metrics.height) }
    }

    private fun shows(button: InventoryButton, title: String, inventory: Boolean): Boolean = when (button.scope) {
        ButtonScope.INVENTORY -> inventory
        ButtonScope.ALL_CONTAINERS -> true
        ButtonScope.TITLE -> button.titleFilter.isNotBlank() && title.contains(button.titleFilter.trim(), ignoreCase = true)
    }

    private fun run(button: InventoryButton) {
        ButtonPainter.pop(button)
        when (button.runs) {
            ButtonRuns.COMMAND -> button.command.trim().removePrefix("/").takeIf(String::isNotEmpty)?.let(Platform.chat::sendCommand)
            ButtonRuns.SHORTCUT -> shortcutOf(button)
                ?.takeIf { it.enabled && ActionStorage.inActiveGroup(ActionStorage.shortcutGroups, it) }
                ?.let { ActionExecutor.executeAll(listOf(it.actions)) }
        }
    }

    private fun shortcutOf(button: InventoryButton): ShortcutAction? =
        ActionStorage.shortcuts.firstOrNull { it.id.toString() == button.shortcutId }

    private fun describe(button: InventoryButton): String {
        val action = when (button.runs) {
            ButtonRuns.COMMAND -> button.command.trim().removePrefix("/").takeIf(String::isNotEmpty)?.let { "/$it" }
            ButtonRuns.SHORTCUT -> shortcutOf(button)?.let { sc ->
                val name = sc.name.ifBlank { sc.alias }
                if (sc.enabled) tr("tooltip.shortcut", "Shortcut: %s", name) else tr("tooltip.shortcut_off", "Shortcut: %s (off)", name)
            }
        } ?: tr("tooltip.nothing", "Doesn't do anything")
        return if (button.label.isBlank()) action else "${button.label}\n$action"
    }

    private const val LIVE_MS = 250L
}
