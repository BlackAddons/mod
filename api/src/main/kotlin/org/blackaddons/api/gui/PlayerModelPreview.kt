package org.blackaddons.api.gui

/**
 * Player model preview inside a UI panel. Entities render in the extract phase, so [request] only records it
 * and the platform draws it next frame. Optional - may be null.
 */
interface PlayerModelPreview {
    /**
     * Previews the local player in the box (GUI px) at the cosmetic scale (0 flattens, negative mirrors).
     * Call every frame. [key] must be stable across frames - it pairs this frame's render with the next draw;
     * previews no longer requested are dropped.
     */
    fun request(
        x: Float, y: Float, w: Float, h: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float,
        yawDeg: Float, pitchDeg: Float,
        key: Any = MAIN,
        showNameTag: Boolean = false,
    )

    /**
     * Previews another player (Profile Viewer). [textures]/[signature] are Mojang's profile property values
     * (null [textures] = default skin). [focusOnCosmetic] frames the single worn cosmetic instead of the
     * body. [skinTexture] uses a bundled skin path instead of the profile's. [gear] dresses the model. Box and
     * angles as in [request]; call every frame.
     */
    fun requestPlayer(
        uuid: String, name: String, textures: String?, signature: String?,
        x: Float, y: Float, w: Float, h: Float,
        yawDeg: Float, pitchDeg: Float,
        key: Any = MAIN,
        focusOnCosmetic: Boolean = false,
        skinTexture: String? = null,
        showNameTag: Boolean = false,
        gear: PreviewGear? = null,
    )

    /** Call when the editor or profile screen closes. */
    fun clear()

    companion object {
        /** The main preview (editor model, Profile Viewer avatar). */
        const val MAIN: String = "main"
    }
}

/** What a previewed player wears, as raw item stacks in the host's format (`ProfileItem.raw`). Null slots are empty. */
class PreviewGear(
    val helmet: String? = null,
    val chestplate: String? = null,
    val leggings: String? = null,
    val boots: String? = null,
    val mainHand: String? = null,
)
