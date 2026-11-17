package org.blackaddons.api.camera

import java.util.UUID

interface CameraHost {
    var perspective: CameraPerspective
    val eyeX: Double
    val eyeY: Double
    val eyeZ: Double

    /**
     * Draws the world from [entity]'s eyes into the box, at most [fps] times a second per entity, along
     * [yaw]/[pitch] or, when they are null, where the entity looks. Call every frame; false if the entity isn't
     * loaded or a shader pack is active.
     */
    fun drawEntityView(
        entity: UUID,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fps: Int,
        yaw: Float? = null,
        pitch: Float? = null,
        radius: Float = 0f,
    ): Boolean
}

enum class CameraPerspective {
    FIRST_PERSON,
    THIRD_PERSON_BACK,
    THIRD_PERSON_FRONT;

    val isFirstPerson: Boolean get() = this == FIRST_PERSON
}
