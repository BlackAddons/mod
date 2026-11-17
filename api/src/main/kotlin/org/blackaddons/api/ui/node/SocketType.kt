package org.blackaddons.api.ui.node

import java.awt.Color

/** Sockets connect when the input's type [accepts] the output's; by default only the same type. */
class SocketType(
    val id: String,
    val color: Color,
    private val compatible: ((output: SocketType) -> Boolean)? = null,
) {
    fun accepts(output: SocketType): Boolean = compatible?.invoke(output) ?: (output === this)

    override fun toString(): String = "SocketType($id)"
}

/** [multiple] allows several wires; otherwise a new wire replaces the old one. */
class SocketSpec(
    val key: String,
    val label: String,
    val type: SocketType,
    val multiple: Boolean = false,
)
