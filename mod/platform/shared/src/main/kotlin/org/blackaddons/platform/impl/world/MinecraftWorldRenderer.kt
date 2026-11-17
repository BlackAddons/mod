package org.blackaddons.platform.impl.world
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.scaled
import org.blackaddons.api.world.WorldRenderer
import org.blackaddons.core.anim.WorldAnim
import org.blackaddons.core.render.WorldRenderPrefs
import org.blackaddons.platform.compat.drawGeometry
import org.blackaddons.platform.compat.endWorldPass
import org.blackaddons.platform.compat.mainCamera
import org.blackaddons.platform.impl.world.MinecraftWorldRenderer.emitFilledBox
import org.blackaddons.platform.impl.world.MinecraftWorldRenderer.endFrame
import java.awt.Color
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [WorldRenderer] over Fabric's `LevelRenderContext`. Geometry and text go through `compat` because text submission
 * differs per version. Layers come from [WorldRenderLayers]. `throughBlocks` works for geometry and text. Text is
 * batched ([WorldTextBatch]) and submitted in [endFrame] so labels sit on top.
 */
object MinecraftWorldRenderer : WorldRenderer {

    // One rotation every 10s at speed 1.
    private const val BEAM_BASE_PERIOD_MS = 10000L

    // Half-width of the boxes outlining the beam's corner edges.
    private const val BEAM_EDGE_HALF = 0.025f

    private val LINES = WorldRenderLayers.LINES
    private val LINES_DEPTH_BIASED = WorldRenderLayers.LINES_DEPTH_BIASED
    private val LINES_THROUGH = WorldRenderLayers.LINES_THROUGH
    private val FILLED = WorldRenderLayers.FILLED
    private val FILLED_DEPTH_BIASED = WorldRenderLayers.FILLED_DEPTH_BIASED
    private val FILLED_THROUGH = WorldRenderLayers.FILLED_THROUGH

    private var current: LevelRenderContext? = null

    // Render thread only: off-thread appends tear vertices or throw, so we drop them (same as BlackSkija).
    private val frame: LevelRenderContext? get() = current?.takeIf { RenderSystem.isOnRenderThread() }

    private val text = WorldTextBatch()

    fun beginFrame(ctx: LevelRenderContext) { current = ctx }

    fun endFrame() {
        val ctx = current
        text.flush(ctx)
        ctx?.endWorldPass()
        current = null
    }

    override fun line(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Color,
        thickness: Float,
        throughBlocks: Boolean,
        depthPriority: Boolean,
    ) = submit(lineLayer(throughBlocks, depthPriority)) { pose, buf, cam ->
        lineSegment(
            buf, pose,
            (x1 - cam.x).toFloat(), (y1 - cam.y).toFloat(), (z1 - cam.z).toFloat(),
            (x2 - cam.x).toFloat(), (y2 - cam.y).toFloat(), (z2 - cam.z).toFloat(),
            color, thickness,
        )
    }

    override fun boxEdges(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Color,
        thickness: Float,
        throughBlocks: Boolean,
        depthPriority: Boolean,
    ) = submit(lineLayer(throughBlocks, depthPriority)) { pose, buf, cam ->
        val x1 = (minX - cam.x).toFloat(); val y1 = (minY - cam.y).toFloat(); val z1 = (minZ - cam.z).toFloat()
        val x2 = (maxX - cam.x).toFloat(); val y2 = (maxY - cam.y).toFloat(); val z2 = (maxZ - cam.z).toFloat()
        fun edge(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) =
            lineSegment(buf, pose, ax, ay, az, bx, by, bz, color, thickness)
        edge(x1, y1, z1, x2, y1, z1); edge(x2, y1, z1, x2, y1, z2); edge(x2, y1, z2, x1, y1, z2); edge(x1, y1, z2, x1, y1, z1)
        edge(x1, y2, z1, x2, y2, z1); edge(x2, y2, z1, x2, y2, z2); edge(x2, y2, z2, x1, y2, z2); edge(x1, y2, z2, x1, y2, z1)
        edge(x1, y1, z1, x1, y2, z1); edge(x2, y1, z1, x2, y2, z1); edge(x2, y1, z2, x2, y2, z2); edge(x1, y1, z2, x1, y2, z2)
    }

    override fun filledBox(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Color,
        throughBlocks: Boolean,
        depthPriority: Boolean,
    ) = submit(filledLayer(throughBlocks, depthPriority)) { pose, buf, cam ->
        emitFilledBox(
            buf, pose,
            (minX - cam.x).toFloat(), (minY - cam.y).toFloat(), (minZ - cam.z).toFloat(),
            (maxX - cam.x).toFloat(), (maxY - cam.y).toFloat(), (maxZ - cam.z).toFloat(),
            color,
        )
    }

    override fun tracer(
        x: Double, y: Double, z: Double,
        color: Color,
        thickness: Float,
        throughBlocks: Boolean,
    ) = submit(if (throughBlocks) LINES_THROUGH else LINES) { pose, buf, cam ->
        val camera = Minecraft.getInstance().mainCamera
        val look = Vec3.directionFromRotation(camera.xRot(), camera.yRot())
        val cp = camera.position()
        lineSegment(
            buf, pose,
            (cp.x + look.x - cam.x).toFloat(), (cp.y + look.y - cam.y).toFloat(), (cp.z + look.z - cam.z).toFloat(),
            (x - cam.x).toFloat(), (y - cam.y).toFloat(), (z - cam.z).toFloat(),
            color, thickness,
        )
    }

    override fun beacon(
        x: Double, z: Double,
        color: Color,
        fromY: Double,
        toY: Double,
        throughBlocks: Boolean,
    ) = submit(filledLayer(throughBlocks, depthPriority = false)) { pose, buf, cam ->
        val half = 0.25
        val a = WorldAnim.spin(BEAM_BASE_PERIOD_MS, WorldRenderPrefs.beaconSpeed()).toDouble()
        val c = cos(a); val s = sin(a)
        fun wx(dx: Double, dz: Double) = (x + dx * c - dz * s - cam.x).toFloat()
        fun wz(dx: Double, dz: Double) = (z + dx * s + dz * c - cam.z).toFloat()
        val x0 = wx(-half, -half); val z0 = wz(-half, -half)
        val x1 = wx(half, -half);  val z1 = wz(half, -half)
        val x2 = wx(half, half);   val z2 = wz(half, half)
        val x3 = wx(-half, half);  val z3 = wz(-half, half)
        val y0 = (fromY - cam.y).toFloat(); val y1 = (toY - cam.y).toFloat()
        emitBeamWall(buf, pose, x0, z0, x1, z1, y0, y1, color)
        emitBeamWall(buf, pose, x1, z1, x2, z2, y0, y1, color)
        emitBeamWall(buf, pose, x2, z2, x3, z3, y0, y1, color)
        emitBeamWall(buf, pose, x3, z3, x0, z0, y0, y1, color)
        // Thin boxes, not lines: MC's line shader smears end-on.
        val edge = darker(color)
        val e = BEAM_EDGE_HALF
        fun edgeColumn(cx: Float, cz: Float) =
            emitFilledBox(buf, pose, cx - e, y0, cz - e, cx + e, y1, cz + e, edge)
        edgeColumn(x0, z0); edgeColumn(x1, z1); edgeColumn(x2, z2); edgeColumn(x3, z3)
    }

    override fun ring(
        x: Double, y: Double, z: Double,
        radius: Double,
        color: Color,
        thickness: Float,
        throughBlocks: Boolean,
        depthPriority: Boolean,
    ) = submit(lineLayer(throughBlocks, depthPriority)) { pose, buf, cam ->
        val segments = ringSegments(radius, x - cam.x, y - cam.y, z - cam.z)
        val cy = (y - cam.y).toFloat()
        var px = (x + radius - cam.x).toFloat(); var pz = (z - cam.z).toFloat()
        for (i in 1..segments) {
            val ang = 2.0 * PI * i / segments
            val nx = (x + radius * cos(ang) - cam.x).toFloat()
            val nz = (z + radius * sin(ang) - cam.z).toFloat()
            lineSegment(buf, pose, px, cy, pz, nx, cy, nz, color, thickness)
            px = nx; pz = nz
        }
    }

    override fun filledCylinder(
        x: Double, y1: Double, z: Double,
        y2: Double, radius: Double,
        color: Color,
        throughBlocks: Boolean,
        depthPriority: Boolean,
    ) = submit(filledLayer(throughBlocks, depthPriority)) { pose, buf, cam ->
        val segments = (radius * 8).toInt().coerceIn(36, 120)
        val cy1 = (y1 - cam.y).toFloat()
        val cy2 = (y2 - cam.y).toFloat()
        val cx = (x - cam.x).toFloat()
        val cz = (z - cam.z).toFloat()

        val r = color.red / 255f; val g = color.green / 255f; val b = color.blue / 255f; val a = color.alpha / 255f
        fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float) {
            buf.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
            buf.addVertex(pose, bx, by, bz).setColor(r, g, b, a)
            buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)

            buf.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
            buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)
            buf.addVertex(pose, bx, by, bz).setColor(r, g, b, a)
        }

        var px = (x + radius - cam.x).toFloat()
        var pz = (z - cam.z).toFloat()

        for (i in 1..segments) {
            val ang = 2.0 * PI * i / segments
            val nx = (x + radius * cos(ang) - cam.x).toFloat()
            val nz = (z + radius * sin(ang) - cam.z).toFloat()

            // Side wall quad (double-sided)
            tri(px, cy1, pz, px, cy2, pz, nx, cy2, nz)
            tri(px, cy1, pz, nx, cy2, nz, nx, cy1, nz)

            // Top disc triangle (double-sided)
            tri(cx, cy2, cz, px, cy2, pz, nx, cy2, nz)

            // Bottom disc triangle (double-sided)
            tri(cx, cy1, cz, nx, cy1, nz, px, cy1, pz)

            px = nx; pz = nz
        }
    }

    override fun cone(
        x: Double, y: Double, z: Double,
        radius: Double, height: Double,
        yaw: Float,
        segments: Int,
        color: (Float) -> Color,
        throughBlocks: Boolean,
        depthPriority: Boolean,
    ) = submit(filledLayer(throughBlocks, depthPriority)) { pose, buf, cam ->
        val cx = x - cam.x; val cz = z - cam.z
        val baseY = (y - cam.y).toFloat(); val apexY = (y + height - cam.y).toFloat()
        val ax = cx.toFloat(); val az = cz.toFloat()
        val turn = Math.toRadians(yaw.toDouble())
        var px = (cx + radius * cos(turn)).toFloat()
        var pz = (cz + radius * sin(turn)).toFloat()
        for (i in 1..segments) {
            val ang = turn + 2.0 * PI * i / segments
            val nx = (cx + radius * cos(ang)).toFloat()
            val nz = (cz + radius * sin(ang)).toFloat()
            val c = color((i - 1).toFloat() / segments)
            val r = c.red / 255f; val g = c.green / 255f; val b = c.blue / 255f; val a = c.alpha / 255f
            buf.addVertex(pose, px, baseY, pz).setColor(r, g, b, a)
            buf.addVertex(pose, nx, baseY, nz).setColor(r, g, b, a)
            buf.addVertex(pose, ax, apexY, az).setColor(r, g, b, a)

            buf.addVertex(pose, nx, baseY, nz).setColor(r, g, b, a)
            buf.addVertex(pose, px, baseY, pz).setColor(r, g, b, a)
            buf.addVertex(pose, ax, apexY, az).setColor(r, g, b, a)
            px = nx; pz = nz
        }
    }

    override fun text(
        text: String,
        x: Double, y: Double, z: Double,
        color: Color,
        scale: Float,
        throughBlocks: Boolean,
    ) {
        if (frame == null) return
        this.text.add(text, x, y, z, color, scale, throughBlocks)
    }

    override fun isVisible(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean {
        val ctx = frame ?: return true
        return ctx.levelState().cameraRenderState.cullFrustum.isVisible(AABB(minX, minY, minZ, maxX, maxY, maxZ))
    }

    private inline fun submit(rt: RenderType, crossinline emit: (PoseStack.Pose, VertexConsumer, Cam) -> Unit) {
        val ctx = frame ?: return
        val stack = ctx.poseStack()
        val pos = ctx.levelState().cameraRenderState.pos
        val cam = Cam(pos.x, pos.y, pos.z)
        ctx.drawGeometry(rt, stack) { pose, buf -> emit(pose, buf, cam) }
    }

    private fun lineLayer(throughBlocks: Boolean, depthPriority: Boolean): RenderType =
        if (throughBlocks) LINES_THROUGH else if (depthPriority) LINES_DEPTH_BIASED else LINES

    private fun filledLayer(throughBlocks: Boolean, depthPriority: Boolean): RenderType =
        if (throughBlocks) FILLED_THROUGH else if (depthPriority) FILLED_DEPTH_BIASED else FILLED

    /**
     * Fewest segments that keep the ring within a quarter pixel at this distance, capped at the old count. A chord
     * misses by about `r * PI^2 / (2n^2)`, at ~1500 px/rad that's under 0.25 px once `n >= 172 * sqrt(r / d)`. Distant
     * rings were most of the waypoint cost.
     */
    private fun ringSegments(radius: Double, dx: Double, dy: Double, dz: Double): Int {
        val nearest = sqrt((sqrt(dx * dx + dz * dz) - radius).let { it * it } + dy * dy).coerceAtLeast(0.5)
        val needed = ceil(172.0 * sqrt(radius / nearest)).toInt()
        return needed.coerceIn(12, (radius * 8).toInt().coerceIn(90, 240))
    }

    private fun lineSegment(
        buf: VertexConsumer, pose: PoseStack.Pose,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        color: Color, width: Float,
    ) {
        var nx = x2 - x1; var ny = y2 - y1; var nz = z2 - z1
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= len; ny /= len; nz /= len
        val r = color.red / 255f; val g = color.green / 255f; val b = color.blue / 255f; val a = color.alpha / 255f
        buf.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(width)
        buf.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(width)
    }

    /** Winding matches [emitFilledBox]. */
    private fun emitBeamWall(
        buf: VertexConsumer, pose: PoseStack.Pose,
        ax: Float, az: Float, bx: Float, bz: Float,
        y0: Float, y1: Float, color: Color,
    ) {
        val r = color.red / 255f; val g = color.green / 255f; val b = color.blue / 255f; val al = color.alpha / 255f
        fun v(x: Float, y: Float, z: Float) { buf.addVertex(pose, x, y, z).setColor(r, g, b, al) }
        v(ax, y0, az); v(ax, y1, az); v(bx, y1, bz)
        v(ax, y0, az); v(bx, y1, bz); v(bx, y0, bz)
    }

    private fun emitFilledBox(
        buf: VertexConsumer, pose: PoseStack.Pose,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        color: Color,
    ) {
        val r = color.red / 255f; val g = color.green / 255f; val b = color.blue / 255f; val a = color.alpha / 255f
        fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float) {
            // Front face
            buf.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
            buf.addVertex(pose, bx, by, bz).setColor(r, g, b, a)
            buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)
            buf.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
            buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)
            buf.addVertex(pose, dx, dy, dz).setColor(r, g, b, a)
            // Back face (for inside/backside rendering)
            buf.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
            buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)
            buf.addVertex(pose, bx, by, bz).setColor(r, g, b, a)
            buf.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
            buf.addVertex(pose, dx, dy, dz).setColor(r, g, b, a)
            buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)
        }
        quad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2)
        quad(x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1)
        quad(x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1)
        quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2)
        quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1)
        quad(x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2)
    }

    /** Darker, slightly more opaque, for beam edges. */
    private fun darker(c: Color) = c.scaled(0.65f).withAlpha(c.alpha + 20)

    private class Cam(val x: Double, val y: Double, val z: Double)
}
