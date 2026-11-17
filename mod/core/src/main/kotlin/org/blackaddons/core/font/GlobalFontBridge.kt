package org.blackaddons.core.font

import org.blackaddons.api.util.I18n
import java.nio.file.Path

data class GlobalFontRequest(
    val files: List<Path> = emptyList(),
    val bundledResource: String? = null,
    val rasterSize: Float = 11f,
    val oversample2d: Float = 2f,
    val oversampleWorld: Float = 4f,
    val maxOversample: Float = 16f,
    val shiftX: Float = 0f,
    val shiftY: Float = 0f,
    val letterSpacing: Float = 0f,
    val forceBold: Boolean = false,
    val forceItalic: Boolean = false,
    val shadow: FontEffect = FontEffect(),
    val outline: FontEffect = FontEffect(),
) {
    fun screenBase(guiScale: Int): Float = if (oversample2d > 0f) oversample2d else guiScale.coerceAtLeast(1).toFloat()

    fun screenOversample(scale: Int, guiScale: Int): Float =
        (screenBase(guiScale) * scale.coerceAtLeast(1)).coerceAtMost(maxOversample)

    val worldOversamples: List<Float> = buildList {
        var oversample = 1f
        while (oversample < oversampleWorld) {
            add(oversample)
            oversample *= 2f
        }
        add(oversampleWorld)
    }

    fun worldBucket(pixelsPerUnit: Float): Int {
        if (pixelsPerUnit.isNaN()) return worldOversamples.lastIndex
        val index = worldOversamples.indexOfFirst { it >= pixelsPerUnit * WORLD_QUALITY }
        return if (index < 0) worldOversamples.lastIndex else index
    }

    private companion object {
        const val WORLD_QUALITY = 2f
    }
}

data class FontEffect(
    val enabled: Boolean = false,
    val colorArgb: Int = 0,
    val offsetX: Float = 1f,
    val offsetY: Float = 1f,
    val width: Float = 1f,
)

enum class FontSlot { MINECRAFT, WORLD }

sealed interface GlobalFontApplyResult {
    data object Applied : GlobalFontApplyResult
    data class Failed(val message: String) : GlobalFontApplyResult
}

object GlobalFontBridge {
    @Volatile
    var apply: (FontSlot, GlobalFontRequest, (GlobalFontApplyResult) -> Unit) -> Unit = { _, _, done ->
        done(GlobalFontApplyResult.Failed(notReady()))
    }

    @Volatile
    var restore: (FontSlot, (GlobalFontApplyResult) -> Unit) -> Unit = { _, done ->
        done(GlobalFontApplyResult.Failed(notReady()))
    }

    private fun notReady(): String = I18n.trOr(
        "blackaddons.global_font_screen.host_not_ready",
        "The Minecraft font host is not ready",
    )
}
