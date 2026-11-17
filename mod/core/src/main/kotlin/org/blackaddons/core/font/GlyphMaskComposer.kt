package org.blackaddons.core.font

data class GlyphMask(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int,
    val pixels: ByteArray,
) {
    init {
        require(width >= 0 && height >= 0 && pixels.size == width * height)
    }
}

data class ComposedGlyphMask(
    val mask: GlyphMask,
    val fillX: Int,
    val fillY: Int,
)

data class ArgbGlyphMask(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int,
    val pixels: IntArray,
)

fun padGlyphMask(source: GlyphMask, padding: Int): GlyphMask {
    require(padding >= 0)
    if (padding == 0) return source
    val width = source.width + padding * 2
    val height = source.height + padding * 2
    val pixels = ByteArray(width * height)
    for (y in 0 until source.height) {
        source.pixels.copyInto(
            pixels,
            destinationOffset = (y + padding) * width + padding,
            startIndex = y * source.width,
            endIndex = (y + 1) * source.width,
        )
    }
    return GlyphMask(width, height, source.left - padding, source.top + padding, pixels)
}

fun composeGlyphMasks(fill: GlyphMask, border: GlyphMask): ComposedGlyphMask {
    val left = minOf(fill.left, border.left)
    val top = maxOf(fill.top, border.top)
    val right = maxOf(fill.left + fill.width, border.left + border.width)
    val bottom = minOf(fill.top - fill.height, border.top - border.height)
    val width = right - left
    val height = top - bottom
    val pixels = ByteArray(width * height)

    fun overlay(source: GlyphMask) {
        val offsetX = source.left - left
        val offsetY = top - source.top
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val target = (offsetY + y) * width + offsetX + x
                val value = source.pixels[y * source.width + x].toInt() and 0xFF
                if (value > (pixels[target].toInt() and 0xFF)) pixels[target] = value.toByte()
            }
        }
    }

    overlay(border)
    overlay(fill)
    return ComposedGlyphMask(
        GlyphMask(width, height, left, top, pixels),
        fill.left - left,
        top - fill.top,
    )
}

fun composeBlackOutline(fill: GlyphMask, border: GlyphMask, outlineAlpha: Int): ArgbGlyphMask {
    require(outlineAlpha in 0..255)
    val composed = composeGlyphMasks(fill, border)
    val mask = composed.mask
    val borderX = border.left - mask.left
    val borderY = mask.top - border.top
    val pixels = IntArray(mask.width * mask.height)

    for (y in 0 until mask.height) {
        val fillRow = y - composed.fillY
        val borderRow = y - borderY
        val hasFill = fillRow in 0 until fill.height
        val hasBorder = borderRow in 0 until border.height
        if (!hasFill && !hasBorder) continue
        for (x in 0 until mask.width) {
            val fillColumn = x - composed.fillX
            val fillCoverage =
                if (hasFill && fillColumn in 0 until fill.width) fill.pixels[fillRow * fill.width + fillColumn].toInt() and 0xFF else 0
            if (fillCoverage == 255) {
                pixels[y * mask.width + x] = OPAQUE_WHITE
                continue
            }
            val borderColumn = x - borderX
            val borderCoverage =
                if (hasBorder && borderColumn in 0 until border.width) border.pixels[borderRow * border.width + borderColumn].toInt() and 0xFF else 0
            val borderAlpha = (borderCoverage * outlineAlpha + 127) / 255
            val alpha = fillCoverage + (borderAlpha * (255 - fillCoverage) + 127) / 255
            if (alpha == 0) continue
            if (fillCoverage == 0) {
                pixels[y * mask.width + x] = alpha shl 24
                continue
            }
            val channel = ((fillCoverage * 255 + alpha / 2) / alpha).coerceAtMost(255)
            pixels[y * mask.width + x] =
                (alpha shl 24) or (channel shl 16) or (channel shl 8) or channel
        }
    }
    return ArgbGlyphMask(mask.width, mask.height, mask.left, mask.top, pixels)
}

fun composeBlackShadow(source: ArgbGlyphMask, shadowAlpha: Int, offsetX: Int, offsetY: Int): ArgbGlyphMask {
    require(shadowAlpha in 0..255)
    if (shadowAlpha == 0) return source
    val shadowLeft = source.left + offsetX
    val shadowTop = source.top - offsetY
    val left = minOf(source.left, shadowLeft)
    val top = maxOf(source.top, shadowTop)
    val right = maxOf(source.left + source.width, shadowLeft + source.width)
    val bottom = minOf(source.top - source.height, shadowTop - source.height)
    val width = right - left
    val height = top - bottom
    val pixels = IntArray(width * height)
    val sourceX = source.left - left
    val sourceY = top - source.top
    val shadowX = shadowLeft - left
    val shadowY = top - shadowTop
    val sourcePixels = source.pixels

    for (y in 0 until height) {
        val baseRow = y - sourceY
        val shadowRow = y - shadowY
        val hasBase = baseRow in 0 until source.height
        val hasShadow = shadowRow in 0 until source.height
        if (!hasBase && !hasShadow) continue
        for (x in 0 until width) {
            val baseColumn = x - sourceX
            val base = if (hasBase && baseColumn in 0 until source.width) sourcePixels[baseRow * source.width + baseColumn] else 0
            val baseAlpha = base ushr 24
            if (baseAlpha == 255) {
                pixels[y * width + x] = base
                continue
            }
            val shadowColumn = x - shadowX
            val shadowSource =
                if (hasShadow && shadowColumn in 0 until source.width) sourcePixels[shadowRow * source.width + shadowColumn] ushr 24 else 0
            val shadow = (shadowSource * shadowAlpha + 127) / 255
            val alpha = baseAlpha + (shadow * (255 - baseAlpha) + 127) / 255
            if (alpha == 0) continue
            if (baseAlpha == 0) {
                pixels[y * width + x] = alpha shl 24
                continue
            }
            val half = alpha / 2
            val red = (((base ushr 16 and 0xFF) * baseAlpha + half) / alpha).coerceAtMost(255)
            val green = (((base ushr 8 and 0xFF) * baseAlpha + half) / alpha).coerceAtMost(255)
            val blue = (((base and 0xFF) * baseAlpha + half) / alpha).coerceAtMost(255)
            pixels[y * width + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    return ArgbGlyphMask(width, height, left, top, pixels)
}

fun whiteArgbMask(source: GlyphMask): ArgbGlyphMask = ArgbGlyphMask(
    source.width,
    source.height,
    source.left,
    source.top,
    IntArray(source.pixels.size) { index ->
        val alpha = source.pixels[index].toInt() and 0xFF
        (alpha shl 24) or 0x00FFFFFF
    },
)

private const val OPAQUE_WHITE = -1
