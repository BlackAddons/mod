package org.blackaddons.core.features.impl.visual.fme

object FmeTextureCoordinates {
    fun remap(value: Float, sourceMin: Float, sourceMax: Float, targetMin: Float, targetMax: Float): Float {
        val width = sourceMax - sourceMin
        if (width == 0f) return targetMin
        return targetMin + ((value - sourceMin) / width).coerceIn(0f, 1f) * (targetMax - targetMin)
    }
}
