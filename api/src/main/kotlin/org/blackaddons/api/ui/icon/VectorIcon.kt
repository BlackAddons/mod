package org.blackaddons.api.ui.icon

/** Tintable SVG path in its square view box. */
data class VectorIcon(
    val strokeData: String,
    val fillData: String? = null,
    val viewBox: Float = 24f,
    val thickness: Float = 2f,
)
