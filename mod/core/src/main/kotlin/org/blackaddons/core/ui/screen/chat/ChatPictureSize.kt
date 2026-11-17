package org.blackaddons.core.ui.screen.chat

import org.blackaddons.core.backend.chat.ChatPicture

// Missing dimensions get a square box.
internal fun ChatPicture.boxWidth(fallback: Float): Float = if (width > 0) width.toFloat() else fallback

internal fun ChatPicture.boxHeight(fallback: Float): Float = if (height > 0) height.toFloat() else fallback

internal fun ChatPicture.fitScale(maxW: Float, maxH: Float): Float =
    minOf(1f, maxW / boxWidth(maxH), maxH / boxHeight(maxH))
