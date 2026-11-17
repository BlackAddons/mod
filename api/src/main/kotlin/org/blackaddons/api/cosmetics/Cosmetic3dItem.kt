package org.blackaddons.api.cosmetics

/** Content-addressed manifest asset; [format] is set for models. */
data class AssetRef(val sha256: String, val url: String, val format: String? = null)

/** Mirrors BlackVertex's `AttachmentPoint`: [point] names a preset, the rest are deltas on top. */
data class CosmeticAttach(
    val point: String,
    val offsetX: Float = 0f, val offsetY: Float = 0f, val offsetZ: Float = 0f,
    val pitch: Float = 0f, val yaw: Float = 0f, val roll: Float = 0f,
    val scale: Float = 1f,
)

data class CosmeticLayer(
    /** `base` | `overlay` | `emissive`; unknown roles are skipped by the renderer. */
    val role: String,
    val asset: AssetRef,
    /** [Cosmetic3dItem.tints] slot, or null for untinted. */
    val tintSlot: String? = null,
    /** Whether the tint's alpha dims this layer. */
    val supportsAlpha: Boolean = false,
)

/**
 * 3D cosmetic as returned by `POST /v1/cosmetics/resolve`. Unknown roles, attach points and formats are
 * skipped by the renderer, not the parser, to stay back-compatible.
 */
data class Cosmetic3dItem(
    val id: String,
    val attach: CosmeticAttach,
    val model: AssetRef,
    val clip: String? = null,
    val layers: List<CosmeticLayer> = emptyList(),
    /** Slot -> `0xAARRGGBB`; alpha is `0xFF` unless the wire sent 8 hex digits. */
    val tints: Map<String, Int> = emptyMap(),
)
