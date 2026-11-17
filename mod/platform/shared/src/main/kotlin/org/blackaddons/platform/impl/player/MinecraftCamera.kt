package org.blackaddons.platform.impl.player
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import org.blackaddons.api.camera.CameraHost
import org.blackaddons.api.camera.CameraPerspective
import org.blackaddons.platform.compat.mainCamera
import org.blackaddons.platform.impl.world.EntityView
import java.util.UUID

object MinecraftCamera : CameraHost {

    private val mc get() = Minecraft.getInstance()

    override var perspective: CameraPerspective
        get() = when (mc.options.cameraType) {
            CameraType.FIRST_PERSON -> CameraPerspective.FIRST_PERSON
            CameraType.THIRD_PERSON_BACK -> CameraPerspective.THIRD_PERSON_BACK
            CameraType.THIRD_PERSON_FRONT -> CameraPerspective.THIRD_PERSON_FRONT
        }
        set(value) {
            mc.options.cameraType = when (value) {
                CameraPerspective.FIRST_PERSON -> CameraType.FIRST_PERSON
                CameraPerspective.THIRD_PERSON_BACK -> CameraType.THIRD_PERSON_BACK
                CameraPerspective.THIRD_PERSON_FRONT -> CameraType.THIRD_PERSON_FRONT
            }
        }

    override val eyeX: Double get() = mc.mainCamera.position().x
    override val eyeY: Double get() = mc.mainCamera.position().y
    override val eyeZ: Double get() = mc.mainCamera.position().z

    override fun drawEntityView(
        entity: UUID,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fps: Int,
        yaw: Float?,
        pitch: Float?,
        radius: Float,
    ): Boolean = EntityView.draw(entity, yaw, pitch, x, y, w, h, fps, radius)
}
