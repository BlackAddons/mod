package org.blackaddons.api.ui.node

import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.UiKeys
import org.blackaddons.api.ui.widget.UiMouse
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

internal class NodeCanvasInput(private val canvas: NodeCanvas) {

    private val graph get() = canvas.graph
    private val camera get() = canvas.camera
    private val selection get() = canvas.selection
    private val drag get() = canvas.drag
    private val properties get() = canvas.properties

    private enum class Gesture { NONE, PAN, MOVE, MARQUEE, WIRE, MINIMAP, SCRUB }

    private var gesture = Gesture.NONE
    private var gestureButton = -1
    private var pressX = 0f
    private var pressY = 0f
    private var pressWorldX = 0.0
    private var pressWorldY = 0.0
    private var pressedNode = -1
    private var pressedWasSelected = false
    private var pressedShift = false
    private var rightClickPending = false

    var marqueeActive = false
        private set
    val marquee = FloatArray(4)

    var wireAnchor: SocketRef? = null
        private set
    var wireTarget: SocketRef? = null
        private set
    var wireEndX = 0f
        private set
    var wireEndY = 0f
        private set
    private var pickedUp: LinkSpec? = null

    var hoverNode = -1
        private set
    var hoverProperty = -1
        private set
    var hoverWorldX = 0f
        private set
    var hoverWorldY = 0f
        private set
    var hoverSocket: SocketRef? = null
        private set
    var hoverLink = -1
        private set
    private val hoverKey = DoubleArray(7) { Double.NaN }
    private val hoverProbe = DoubleArray(7)

    private val samples = ArrayDeque<DoubleArray>()
    private val clock = TimeSource.Monotonic.markNow()

    private var lastClick = TimeSource.Monotonic.markNow()
    private var lastClickNode = -1

    private val scratch = IntList(64)
    private val frame = FloatArray(4)

    fun refreshHover() {
        if (gesture != Gesture.NONE || !canvas.box.has(UiMouse.x, UiMouse.y)) {
            clearHover()
            hoverKey[0] = Double.NaN
            return
        }
        val key = hoverProbe
        key[0] = UiMouse.x.toDouble(); key[1] = UiMouse.y.toDouble()
        key[2] = camera.originX; key[3] = camera.originY; key[4] = camera.zoom
        key[5] = graph.visualVersion.toDouble(); key[6] = graph.structureVersion.toDouble()
        if (key.contentEquals(hoverKey)) return
        key.copyInto(hoverKey)
        val wx = canvas.worldX(UiMouse.x).toFloat()
        val wy = canvas.worldY(UiMouse.y).toFloat()
        if (canvas.minimap.contains(UiMouse.x, UiMouse.y)) return clearHover()
        hoverSocket = canvas.socketAt(wx, wy)
        hoverNode = canvas.nodeAt(wx, wy)?.id ?: -1
        hoverProperty = graph.node(hoverNode)?.takeIf { canvas.propertiesShown }?.let { NodeMetrics.propertyAt(it, wx, wy) } ?: -1
        hoverWorldX = wx
        hoverWorldY = wy
        hoverLink = if (hoverNode < 0 && hoverSocket == null) canvas.linkAt(wx, wy)?.id ?: -1 else -1
    }

    private fun clearHover() {
        hoverNode = -1
        hoverProperty = -1
        hoverSocket = null
        hoverLink = -1
    }

    fun mouseClicked(mouse: MouseButtonInput): Boolean {
        properties.mouseClicked(mouse)?.let { return it }
        if (!canvas.box.has(mouse.x, mouse.y)) return false
        if (gesture != Gesture.NONE) cancelGesture()
        val mx = mouse.x
        val my = mouse.y
        camera.stop()
        gestureButton = mouse.button
        pressX = mx
        pressY = my
        pressWorldX = canvas.worldX(mx)
        pressWorldY = canvas.worldY(my)
        val wx = pressWorldX.toFloat()
        val wy = pressWorldY.toFloat()

        if (canvas.minimap.contains(mx, my)) {
            gesture = Gesture.MINIMAP
            canvas.minimap.jump(mx, my)
            return true
        }
        when (mouse.button) {
            Keys.MOUSE_MIDDLE -> startPan()
            Keys.MOUSE_RIGHT -> {
                startPan()
                rightClickPending = true
            }
            Keys.MOUSE_LEFT -> leftPress(wx, wy)
            else -> return false
        }
        return true
    }

    private fun leftPress(wx: Float, wy: Float) {
        val socket = canvas.socketAt(wx, wy)
        if (socket != null) return startWire(socket)

        val node = canvas.nodeAt(wx, wy)
        if (node != null && canvas.detail.ordinal < NodeDetail.LOW.ordinal && NodeMetrics.toggleHit(node, wx, wy)) {
            canvas.onToggle(node)
            return
        }
        val row = if (node != null && canvas.propertiesShown) NodeMetrics.propertyAt(node, wx, wy) else -1
        if (node != null && row >= 0) {
            lastClickNode = -1
            if (properties.press(node, node.properties[row], wx, pressX)) gesture = Gesture.SCRUB
            return
        }
        if (node != null) {
            val double = node.id == lastClickNode && lastClick.elapsedNow() < DOUBLE_CLICK
            lastClick = TimeSource.Monotonic.markNow()
            lastClickNode = node.id
            if (double && !UiKeys.shift && !UiKeys.ctrl) {
                lastClickNode = -1
                selection.set(node.id)
                canvas.onActivate(node)
                return
            }
            pressedNode = node.id
            pressedWasSelected = node.id in selection
            pressedShift = UiKeys.shift
            when {
                UiKeys.shift -> selection.add(node.id)
                UiKeys.ctrl -> selection.remove(node.id)
                !pressedWasSelected -> selection.set(node.id)
            }
            gesture = if (node.id in selection) Gesture.MOVE else Gesture.NONE
            return
        }
        lastClickNode = -1

        val group = canvas.groupAt(wx, wy)
        if (group != null && graph.groupBounds(group, frame) && wy < frame[1] + NodeMetrics.GROUP_HEADER) {
            if (canvas.detail.ordinal < NodeDetail.LOW.ordinal && NodeMetrics.groupToggleHit(group, frame, wx, wy)) {
                canvas.onGroupToggle(group)
                return
            }
            val members = group.memberIds.filter { graph.node(it) != null }
            if (members.isNotEmpty()) {
                if (!UiKeys.shift) selection.clear()
                members.forEach(selection::add)
                pressedNode = members.first()
                pressedWasSelected = false
                pressedShift = false
                gesture = Gesture.MOVE
                return
            }
        }

        if (UiKeys.ctrl) {
            canvas.linkAt(wx, wy)?.let { link ->
                canvas.disconnect(link)
                return
            }
        }
        if (!UiKeys.shift && !UiKeys.ctrl) selection.clear()
        gesture = Gesture.MARQUEE
        marqueeActive = false
        marquee[0] = pressX - canvas.boxX; marquee[1] = pressY - canvas.boxY
        marquee[2] = marquee[0]; marquee[3] = marquee[1]
    }

    private fun startPan() {
        gesture = Gesture.PAN
        samples.clear()
    }

    private fun startWire(socket: SocketRef) {
        val inputSpec = if (socket.output) null else graph.node(socket.node)?.inputs?.getOrNull(socket.slot)
        val existing = if (inputSpec != null && !inputSpec.multiple) graph.linksAt(socket.node, socket.slot, output = false).firstOrNull() else null
        if (existing != null) {
            pickedUp = existing.spec
            graph.disconnect(existing.id)
            wireAnchor = SocketRef(existing.from, existing.fromSlot, output = true)
        } else {
            pickedUp = null
            wireAnchor = socket
        }
        wireTarget = null
        wireEndX = pressWorldX.toFloat()
        wireEndY = pressWorldY.toFloat()
        gesture = Gesture.WIRE
    }

    // `pointer`, because `drag` is the node drag in progress.
    fun mouseDragged(pointer: MouseDragInput): Boolean {
        if (properties.mouseDragged(pointer)) return true
        if (gesture == Gesture.NONE) return false
        val mx = pointer.x
        val my = pointer.y
        when (gesture) {
            Gesture.PAN -> {
                if (rightClickPending && hypot(mx - pressX, my - pressY) > CLICK_SLOP) rightClickPending = false
                camera.panBy(pointer.dx, pointer.dy)
                samples.addLast(doubleArrayOf(pointer.dx.toDouble(), pointer.dy.toDouble(), clockMs()))
                while (samples.size > 8) samples.removeFirst()
            }
            Gesture.MOVE -> {
                if (!drag.active && hypot(mx - pressX, my - pressY) > CLICK_SLOP) drag.begin(selection.toIntArray())
                if (drag.active) {
                    drag.dx = (canvas.worldX(mx) - pressWorldX).toFloat()
                    drag.dy = (canvas.worldY(my) - pressWorldY).toFloat()
                    val anchor = graph.node(pressedNode)
                    if (UiKeys.shift && anchor != null) {
                        drag.dx = snap(anchor.x + drag.dx) - anchor.x
                        drag.dy = snap(anchor.y + drag.dy) - anchor.y
                    }
                }
            }
            Gesture.MARQUEE -> {
                marquee[2] = mx - canvas.boxX
                marquee[3] = my - canvas.boxY
                if (!marqueeActive && hypot(mx - pressX, my - pressY) > CLICK_SLOP) marqueeActive = true
            }
            Gesture.WIRE -> {
                wireEndX = canvas.worldX(mx).toFloat()
                wireEndY = canvas.worldY(my).toFloat()
                wireTarget = findWireTarget(wireEndX, wireEndY)
            }
            Gesture.MINIMAP -> canvas.minimap.jump(mx, my)
            Gesture.SCRUB -> properties.scrubDrag(mx)
            Gesture.NONE -> {}
        }
        return true
    }

    fun mouseReleased(mouse: MouseButtonInput): Boolean {
        properties.mouseReleased(mouse)
        if (gesture == Gesture.NONE || mouse.button != gestureButton) return false
        val mx = mouse.x
        val my = mouse.y
        when (gesture) {
            Gesture.PAN -> {
                if (rightClickPending) {
                    val wx = canvas.worldX(mx).toFloat()
                    val wy = canvas.worldY(my).toFloat()
                    val node = canvas.nodeAt(wx, wy)
                    if (node != null && node.id !in selection) selection.set(node.id)
                    val link = if (node == null) canvas.linkAt(wx, wy) else null
                    val group = if (node == null && link == null) canvas.groupAt(wx, wy) else null
                    rightClickPending = false
                    endGesture()
                    canvas.onContextMenu(CanvasPoint(node, link, wx, wy, group))
                    return true
                }
                coast()
            }
            Gesture.MOVE -> {
                if (drag.active) {
                    drag.end(commit = true)
                } else if (pressedWasSelected && pressedShift && pressedNode >= 0) {
                    selection.remove(pressedNode)
                } else if (pressedWasSelected && !UiKeys.shift && !UiKeys.ctrl && pressedNode >= 0) {
                    selection.set(pressedNode)
                }
            }
            Gesture.MARQUEE -> if (marqueeActive) applyMarquee()
            Gesture.WIRE -> finishWire()
            Gesture.SCRUB -> properties.finishScrub()
            Gesture.MINIMAP, Gesture.NONE -> {}
        }
        endGesture()
        return true
    }

    fun cancelGesture() {
        properties.settle()
        when (gesture) {
            Gesture.MOVE -> if (drag.active) drag.end(commit = false)
            Gesture.WIRE -> pickedUp?.let { graph.connect(it.from, it.fromSlot, it.to, it.toSlot) }
            Gesture.SCRUB -> properties.cancelScrub()
            else -> {}
        }
        rightClickPending = false
        endGesture()
    }

    private fun endGesture() {
        gesture = Gesture.NONE
        gestureButton = -1
        pressedNode = -1
        marqueeActive = false
        wireAnchor = null
        wireTarget = null
        pickedUp = null
        properties.endScrub()
    }

    fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (properties.mouseScrolled(wheel)) return true
        if (!canvas.box.has(wheel.x, wheel.y)) return false
        when {
            UiKeys.ctrl -> camera.panBy(wheel.amount * SCROLL_PAN, 0f)
            UiKeys.shift -> camera.panBy(0f, wheel.amount * SCROLL_PAN)
            else -> camera.zoomAt(wheel.amount.toDouble(), wheel.x - canvas.boxX, wheel.y - canvas.boxY)
        }
        return true
    }

    fun keyPressed(key: KeyInput): Boolean {
        if (properties.keyPressed(key)) return true
        if (gesture == Gesture.SCRUB && key.code == Keys.ESCAPE) {
            cancelGesture()
            return true
        }
        if (gesture != Gesture.NONE) return false
        val ctrl = key.ctrl
        val shift = key.shift
        when {
            key.code == Keys.Z && ctrl && shift -> canvas.history.redo()
            key.code == Keys.Z && ctrl -> canvas.history.undo()
            key.code == Keys.Y && ctrl -> canvas.history.redo()
            key.code == Keys.A && ctrl -> canvas.selectAll()
            ctrl -> return false
            key.code == Keys.DELETE || key.code == Keys.BACKSPACE || key.code == Keys.X -> canvas.deleteSelection()
            key.code == Keys.HOME -> canvas.frameAll()
            key.code == Keys.F -> canvas.frameSelection()
            key.code == Keys.LEFT -> nudge(-1f, 0f, shift)
            key.code == Keys.RIGHT -> nudge(1f, 0f, shift)
            key.code == Keys.UP -> nudge(0f, -1f, shift)
            key.code == Keys.DOWN -> nudge(0f, 1f, shift)
            else -> return false
        }
        return true
    }

    private fun nudge(dx: Float, dy: Float, far: Boolean) {
        if (selection.isEmpty) return
        val step = if (far) NUDGE_FAR else NUDGE
        canvas.history.execute(MoveNodes(selection.toIntArray(), dx * step, dy * step, nudge = true))
    }

    private fun coast() {
        if (samples.size < 2) return
        val now = clockMs()
        val recent = samples.filter { now - it[2] < COAST_WINDOW_MS }
        if (recent.size < 2) return
        val span = (recent.last()[2] - recent.first()[2]).coerceAtLeast(1.0) / 1000.0
        val vx = recent.drop(1).sumOf { it[0] } / span
        val vy = recent.drop(1).sumOf { it[1] } / span
        camera.fling(vx.toFloat(), vy.toFloat())
    }

    private fun clockMs(): Double = clock.elapsedNow().toDouble(DurationUnit.MILLISECONDS)

    private fun snap(v: Float): Float = (v / NodeMetrics.GRID).roundToInt() * NodeMetrics.GRID

    private fun applyMarquee() {
        val x0 = canvas.worldX(canvas.boxX + minOf(marquee[0], marquee[2])).toFloat()
        val y0 = canvas.worldY(canvas.boxY + minOf(marquee[1], marquee[3])).toFloat()
        val x1 = canvas.worldX(canvas.boxX + maxOf(marquee[0], marquee[2])).toFloat()
        val y1 = canvas.worldY(canvas.boxY + maxOf(marquee[1], marquee[3])).toFloat()
        scratch.clear()
        graph.nodeIndex.query(x0, y0, x1, y1, scratch)
        val remove = UiKeys.ctrl
        for (i in 0 until scratch.size) {
            val node = graph.node(scratch[i]) ?: continue
            val touches = node.x < x1 && node.x + node.width > x0 && node.y < y1 && node.y + node.height > y0
            if (!touches) continue
            if (remove) selection.remove(node.id) else selection.add(node.id)
        }
    }

    private fun findWireTarget(wx: Float, wy: Float): SocketRef? {
        val anchor = wireAnchor ?: return null
        val accept = { ref: SocketRef -> ref.output != anchor.output && canvas.canConnect(specOf(anchor, ref)) }
        canvas.socketAt(wx, wy, accept)?.let { return it }
        val node = canvas.nodeAt(wx, wy) ?: return null
        val count = if (anchor.output) node.inputs.size else node.outputs.size
        for (slot in 0 until count) {
            val ref = SocketRef(node.id, slot, output = !anchor.output)
            if (accept(ref)) return ref
        }
        return null
    }

    private fun specOf(a: SocketRef, b: SocketRef): LinkSpec {
        val out = if (a.output) a else b
        val input = if (a.output) b else a
        return LinkSpec(out.node, out.slot, input.node, input.slot)
    }

    private fun finishWire() {
        val anchor = wireAnchor ?: return
        val target = wireTarget
        val original = pickedUp
        if (original != null) graph.connect(original.from, original.fromSlot, original.to, original.toSlot)
        val spec = target?.let { specOf(anchor, it) }
        when {
            spec != null && spec == original -> {}
            spec != null && original != null -> canvas.history.execute(CompoundCommand(listOf(Disconnect(original), Connect(spec))))
            spec != null -> canvas.history.execute(Connect(spec))
            original != null -> canvas.history.execute(Disconnect(original))
        }
    }

    companion object {
        const val CLICK_SLOP = 3f
        private const val SCROLL_PAN = 40f
        private const val NUDGE = 10f
        private const val NUDGE_FAR = 50f
        private val DOUBLE_CLICK = 350.milliseconds
        private const val COAST_WINDOW_MS = 90.0
    }
}
