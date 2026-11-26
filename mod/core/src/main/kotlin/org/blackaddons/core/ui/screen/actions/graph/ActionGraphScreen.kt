package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.node.GraphNode
import org.blackaddons.api.ui.node.NodeCanvas
import org.blackaddons.api.ui.node.NodeGraph
import org.blackaddons.api.ui.node.NodeMetrics
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.world.Vec3d
import org.blackaddons.core.actions.*
import org.blackaddons.core.actions.steps.EndStep
import org.blackaddons.core.features.impl.settings.Actions
import org.blackaddons.core.ui.screen.actions.ActionsPage
import org.blackaddons.core.ui.screen.actions.ActionsScreen
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.LocationBar

/** Actions as a node graph, one board per tab. [fromList]: the list view is under it, so going back pops. */
internal class ActionGraphScreen private constructor(private var page: ActionsPage, private val fromList: Boolean) : Panel() {

    private val sockets = ActionGraphSocketSet(
        run = actionTr("graph.socket.run", "Run"),
        input = actionTr("graph.socket.in", "In"),
        next = actionTr("graph.socket.next", "Next"),
        after = actionTr("graph.socket.after", "After"),
        isTrue = actionTr("graph.socket.true", "True"),
        isFalse = actionTr("graph.socket.false", "False"),
        loop = actionTr("graph.socket.loop", "Loop"),
    )

    private val graph = NodeGraph()
    private val canvas = widgets.add(NodeCanvas(graph))

    private var profile: ActionProfile = ActionStorage.activeProfile
    private var triggers: List<TriggerRef> = emptyList()
    private var triggerSignature = 0
    private var looseSteps = 0
    private var layoutDirty = false
    private val frames = ActionGraphGroups(graph)
    private val edits = ActionGraphEdits(canvas, sockets, ::commit, ::reload)
    private val menus = ActionGraphMenus(canvas, edits, frames, { page }, { profile }, ::defaultPositions) {
        followProfile()
        focus(it)
    }

    private val toolbar = ActionGraphToolbar(
        widgets, canvas,
        onList = {
            UiManager.pop()
            if (!fromList) ActionsScreen.open(page)
        },
        onBoard = ::switchBoard,
        onReset = ::resetLayout,
        closest = ::closestWaypoint,
        onClosest = ::focus,
        onAdd = menus::openAddMenu,
    )

    private val hint = actionTr(
        "graph.hint",
        "Wheel: zoom · Middle/right drag: pan · Drag a socket to wire · Right-click: menu · Double-click: edit",
    )

    init {
        canvas.showMinimap = Actions.graphMinimap
        canvas.connectionFilter = { spec -> !ActionGraphCompiler.makesCycle(graph, spec) }
        canvas.onActivate = edits::edit
        canvas.onPropertyEdited = edits::afterPropertyEdit
        canvas.onToggle = edits::toggle
        canvas.onGroupToggle = menus::toggleGroup
        canvas.onContextMenu = menus::contextMenu
        canvas.groupFollowers = frames::followers
        canvas.onEdited = { structural ->
            if (structural) commit() else layoutDirty = true
            frames.sync(page.groups(profile))
        }
        keys.on(Keys.ESCAPE) { UiManager.pop() }
        keys.on(Keys.C, ctrl = true) { edits.copySteps(edits.selectedNodes()) }
        keys.on(Keys.D, ctrl = true) { edits.duplicateSteps(edits.selectedNodes()) }
        keys.on(Keys.V, ctrl = true) {
            edits.paste(canvas.camera.toWorldX(UiMouse.x).toFloat(), canvas.camera.toWorldY(UiMouse.y - ActionGraphToolbar.HEIGHT).toFloat())
        }
        ActionsScreen.lastPage = page
        rebuild(restoreCamera = true)
    }

    override fun onShow() {
        widgets.focus(canvas)
    }

    override fun onClose() {
        canvas.cancelGesture()
        saveView()
        canvas.release()
    }

    override fun keyPressed(key: KeyInput): Boolean =
        super.keyPressed(key) || (!canvas.focused && canvas.keyPressed(key))

    override fun charTyped(typed: CharInput): Boolean =
        super.charTyped(typed) || (!canvas.focused && canvas.charTyped(typed))

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        followProfile()
        val sw = UiManager.screenWidth
        val sh = UiManager.screenHeight
        val barH = ActionGraphToolbar.HEIGHT
        val footH = if (page == ActionsPage.LOCATION) FOOT_H else 0f

        canvas.render(0f, barH, sw, sh - barH - footH)
        if (footH > 0f) {
            Render.rect(0f, sh - footH, sw, footH, Theme.SURFACE)
            Render.rect(0f, sh - footH, sw, 1f, Theme.BORDER)
            LocationBar.draw(Theme.PADDING, sh - footH + (footH - LocationBar.HEIGHT) / 2f)
        }
        Render.text(
            hint, Theme.PADDING, barH + Theme.PADDING_SMALL, Theme.FONT_SMALL, Theme.TEXT_MUTED.withAlpha(170), Fonts.regular,
            maxWidth = sw - Theme.PADDING * 2f,
        )

        toolbar.draw(page, profile.name, looseSteps, sw, mouseX, mouseY)
        toolbar.renderOverlay(mouseX, mouseY)
        canvas.renderOverlay(mouseX, mouseY)
    }

    private fun switchBoard(next: ActionsPage) {
        if (next == page) return
        canvas.cancelGesture()
        saveView()
        page = next
        ActionsScreen.lastPage = next
        rebuild(restoreCamera = true)
    }

    private fun followProfile() {
        if (ActionStorage.activeProfile !== profile) {
            saveView()
            rebuild(restoreCamera = true)
            return
        }
        if (signature() != triggerSignature) reload()
    }

    private fun reload() {
        saveLayout(ActionGraphCompiler.compile(graph, writeSteps = false))
        rebuild(restoreCamera = false)
    }

    private fun rebuild(restoreCamera: Boolean) {
        profile = ActionStorage.activeProfile
        val layout = ActionGraphLayout.load(profile.id, page)
        canvas.history.clear()
        canvas.selection.clear()
        graph.clear()
        val builder = ActionGraphBuilder(graph, sockets, layout.positions, ActionGraphStyle::style, ::stepHeight)
        builder.build(laidOutTriggers(), layout.looseChains(), ::bandOf)
        frames.load(page.groups(profile))
        triggers = presentTriggers()
        toolbar.select(page)
        looseSteps = layout.loose.sumOf { chain -> chain.steps.count { it.type !== EndStep } }
        triggerSignature = signature()
        layoutDirty = false
        if (!restoreCamera) return
        val zoom = layout.zoom
        val cx = layout.cameraX
        val cy = layout.cameraY
        if (zoom != null && cx != null && cy != null) {
            canvas.camera.set(cx, cy, zoom)
            canvas.frameOnFirstDraw = false
        } else {
            canvas.frameOnFirstDraw = true
            if (canvas.width > 0f) canvas.frameAll(smooth = false)
        }
    }

    private fun signature(): Int {
        var h = 1
        fun mix(items: List<Any>) {
            h = h * 31 + items.size
            for (item in items) h = h * 31 + System.identityHashCode(item)
        }
        when (page) {
            ActionsPage.CHAT -> mix(profile.chatActions)
            ActionsPage.WAYPOINT -> {
                mix(profile.waypoints)
                profile.waypoints.forEach { mix(it.actions) }
            }
            ActionsPage.SHORTCUT -> mix(profile.shortcuts)
            ActionsPage.LOCATION -> mix(profile.locationActions)
        }
        return h
    }

    private fun presentTriggers(): List<TriggerRef> = graph.nodes().mapNotNull { (it.payload as? TriggerPayload)?.ref }.toList()

    private fun commit() {
        val result = ActionGraphCompiler.compile(graph)
        val present = presentTriggers()
        triggers.filter { ref -> present.none { it === ref } }.forEach { it.detach(profile) }
        present.forEach { it.attach(profile) }
        triggers = present
        looseSteps = result.looseSteps
        ActionStorage.save()
        saveLayout(result)
        triggerSignature = signature()
    }

    private fun saveLayout(result: ActionGraphCompiler.Result) {
        val camera = canvas.camera
        val data = ActionGraphLayout.FileData(
            cameraX = camera.originX,
            cameraY = camera.originY,
            zoom = camera.zoom,
            positions = result.positions.toMutableMap(),
            loose = result.loose.map { ActionGraphLayout.LooseFile(it.steps, it.positions) }.toMutableList(),
        )
        ActionGraphLayout.save(profile.id, page, data)
        layoutDirty = false
    }

    private fun saveView() {
        if (layoutDirty) return saveLayout(ActionGraphCompiler.compile(graph, writeSteps = false))
        val camera = canvas.camera
        val data = ActionGraphLayout.load(profile.id, page)
        data.cameraX = camera.originX
        data.cameraY = camera.originY
        data.zoom = camera.zoom
        ActionGraphLayout.save(profile.id, page, data)
    }

    private fun laidOutTriggers(): List<TriggerRef> {
        val groups = page.groups(profile)
        return TriggerRef.of(profile, page).sortedBy { ref ->
            groups.indexOfFirst { it.id == ref.owner.groupId }.let { if (it < 0) groups.size else it }
        }
    }

    private fun bandOf(ref: TriggerRef): Any? = page.groups(profile).firstOrNull { it.id == ref.owner.groupId }

    /** Reset Layout positions. A node stays with its group if the others stay. A whole group resets together. */
    private fun defaultPositions(ids: IntArray): Map<Int, FloatArray> {
        val result = ActionGraphCompiler.compile(graph, writeSteps = false)
        val fresh = NodeGraph()
        val builder = ActionGraphBuilder(fresh, sockets, onNode = ActionGraphStyle::style, stepHeight = ::stepHeight)
        builder.build(laidOutTriggers(), result.loose.map { LooseChain(it.steps, mutableListOf()) }, ::bandOf)
        fun defaultOf(id: Int): GraphNode? = result.keys[id]?.let(builder.keys::get)?.let(fresh::node)

        val resetting = ids.toHashSet()
        return ids.asList().mapNotNull { id ->
            val target = defaultOf(id) ?: return@mapNotNull null
            val group = graph.groupOf(id) ?: return@mapNotNull id to floatArrayOf(target.x, target.y)
            val anchors = group.memberIds.filter { it !in resetting && graph.node(it) != null && defaultOf(it) != null }
            if (anchors.isEmpty()) return@mapNotNull id to floatArrayOf(target.x, target.y)
            val dx = anchors.minOf { graph.node(it)!!.x } - anchors.minOf { defaultOf(it)!!.x }
            val dy = anchors.minOf { graph.node(it)!!.y } - anchors.minOf { defaultOf(it)!!.y }
            id to floatArrayOf(target.x + dx, target.y + dy)
        }.toMap()
    }

    private fun resetLayout() {
        canvas.cancelGesture()
        val result = ActionGraphCompiler.compile(graph, writeSteps = false)
        val data = ActionGraphLayout.FileData(loose = result.loose.mapTo(mutableListOf()) { ActionGraphLayout.LooseFile(it.steps) })
        ActionGraphLayout.save(profile.id, page, data)
        rebuild(restoreCamera = false)
        canvas.separateGroups()
        layoutDirty = true
        canvas.frameAll()
    }

    private fun stepHeight(step: ActionStep): Float =
        NodeMetrics.height(sockets.outputsFor(step.type).size, subtitle = false, ActionGraphProperties.of(step).size)

    private fun focus(owner: Groupable) {
        val ids = graph.nodes().filter { (it.payload as? TriggerPayload)?.ref?.owner === owner }.map { it.id }.toList()
        if (ids.isEmpty()) return
        canvas.selection.clear()
        ids.forEach(canvas.selection::add)
        canvas.frameSelection()
    }

    private fun closestWaypoint(): WaypointDefinition? {
        if (!Platform.player.isInGame) return null
        val pos = Platform.player.pos
        return profile.waypoints.minByOrNull { pos.distanceSqr(Vec3d(it.x, it.y, it.z)) }
    }

    companion object {
        private const val FOOT_H = 28f

        fun open(page: ActionsPage, fromList: Boolean) {
            UiManager.push(ActionGraphScreen(page, fromList))
        }
    }
}
