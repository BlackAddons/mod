package org.blackaddons.extras.features.impl.extras.storage

internal enum class ContainerType { ENDER_CHEST, BACKPACK }

/** Drives the selector icon. */
internal enum class SlotState { OPEN, EMPTY, LOCKED }

/** [page] parsed from the title. */
internal data class OpenPage(val type: ContainerType, val page: Int, val title: String)

/**
 * [icon] is the raw overview stack. [openable] is false for empty or locked backpack slots, they still show in the
 * selector though.
 */
internal data class StorageRef(
    val type: ContainerType,
    val page: Int,
    val label: String,
    val icon: String? = null,
    val state: SlotState = SlotState.OPEN,
    /** Slot in the live overview menu for click relay, -1 if we don't know. */
    val slot: Int = -1,
) {
    val openable: Boolean get() = state == SlotState.OPEN
    val key: String get() = StoragePatterns.containerKey(type, page)
    val command: String get() = when (type) {
        ContainerType.ENDER_CHEST -> "enderchest $page"
        ContainerType.BACKPACK -> "backpack $page"
    }
}
