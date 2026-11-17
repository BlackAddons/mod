package org.blackaddons.platform.impl.world
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlobalSettingsUniform
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.world.entity.Entity
import org.blackaddons.api.ui.render.Render
import org.blackaddons.blackskija.api.Skija
import org.blackaddons.blackskija.api.SkijaTextures
import org.blackaddons.platform.compat.newViewTarget
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/*
 * The world from an entity's view, drawn into HUD boxes. Draw it every frame u want it. Each version's GameRenderer
 * mixin renders due views into their targets after HUD extraction. Views not drawn this frame get dropped.
 *
 * Shortcut: views cull with the player's occlusion graph, so sections only the entity sees might be missing.
 */
object EntityView {
    class View(
        val entity: Entity,
        val yaw: Float,
        val pitch: Float,
        val target: RenderTarget,
        val globals: GlobalSettingsUniform,
        val projection: ProjectionMatrixBuffer,
    )

    private class Slot(var target: RenderTarget) {
        val globals = GlobalSettingsUniform()
        val projection = ProjectionMatrixBuffer(LABEL)
        var request: View? = null
        var interval = Duration.ZERO
        var nextPass: TimeSource.Monotonic.ValueTimeMark? = null
    }

    private val slots = HashMap<UUID, Slot>()

    @JvmStatic
    var current: View? = null
        private set

    @JvmStatic
    val rendering: Boolean get() = current != null

    fun draw(uuid: UUID, yaw: Float?, pitch: Float?, x: Float, y: Float, w: Float, h: Float, fps: Int, radius: Float): Boolean {
        if (fps <= 0 || IrisCompat.shaderPackInUse()) return false
        val mc = Minecraft.getInstance()
        val entity = mc.level?.getEntity(uuid) ?: return false
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val viewYaw = yaw ?: entity.getViewYRot(partialTick)
        val viewPitch = pitch ?: entity.getViewXRot(partialTick)
        val window = mc.window
        val slot = slots.getOrPut(uuid) { Slot(newViewTarget(LABEL, window.width, window.height)) }
        if (slot.target.width != window.width || slot.target.height != window.height) {
            slot.target.destroyBuffers()
            slot.target = newViewTarget(LABEL, window.width, window.height)
            slot.nextPass = null
        }
        slot.request = View(entity, viewYaw, viewPitch, slot.target, slot.globals, slot.projection)
        slot.interval = 1.seconds / fps
        val image = slot.target.colorTextureView?.let { SkijaTextures.wrap(it, premultiplied = false) } ?: return false
        /* Render targets are bottom-up. */
        Render.push()
        Render.translate(0f, y + y + h)
        Render.scale(1f, -1f)
        Skija.image(image, x, y, w, h, radius)
        Render.pop()
        return true
    }

    /* Drawn this frame and due. */
    @JvmStatic
    fun due(): List<View> {
        val due = ArrayList<View>()
        val iterator = slots.values.iterator()
        while (iterator.hasNext()) {
            val slot = iterator.next()
            val request = slot.request
            slot.request = null
            if (request == null) {
                slot.target.destroyBuffers()
                slot.globals.close()
                slot.projection.close()
                iterator.remove()
            } else if (slot.nextPass?.hasPassedNow() != false) {
                slot.nextPass = TimeSource.Monotonic.markNow() + slot.interval
                due += request
            }
        }
        return due
    }

    @JvmStatic
    fun begin(view: View) {
        current = view
    }

    @JvmStatic
    fun end() {
        current = null
    }

    private const val LABEL = "BlackAddons entity view"
}
