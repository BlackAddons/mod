package org.blackaddons.api.feature

import org.blackaddons.api.ui.icon.VectorIcon

/** Entry in a feature row's right-click menu, above the default ones. */
class MenuAction internal constructor(
    val id: String,
    val label: String,
    /** Lang namespace: whoever declared the action. */
    val namespace: String,
    val icon: VectorIcon?,
    val danger: Boolean,
    val confirm: String?,
    val run: () -> Unit,
)

/**
 * Adds actions to a feature row's right-click menu; an extension's go into its host's menu.
 * ```
 * menuAction("Clear Cache", icon = LucideIcons.rotateCcw, confirm = "Forget every cached room?") { cache.clear() }
 * ```
 */
interface MenuActionProvider {

    val id: String

    val menuActions: MutableList<MenuAction>

    fun menuAction(
        label: String,
        icon: VectorIcon? = null,
        danger: Boolean = false,
        confirm: String? = null,
        id: String = label,
        run: () -> Unit,
    ): MenuAction = MenuAction(id, label, this.id, icon, danger, confirm, run).also(menuActions::add)
}
