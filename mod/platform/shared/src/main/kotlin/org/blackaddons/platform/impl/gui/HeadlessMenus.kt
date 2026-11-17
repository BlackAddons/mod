package org.blackaddons.platform.impl.gui

import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.AbstractContainerMenu

/** The menu a headless `ContainerOpenEvent` took over. It has no screen to carry its title. */
object HeadlessMenus {

    private var menu: AbstractContainerMenu? = null
    private var title: String? = null

    fun adopt(menu: AbstractContainerMenu, title: String) {
        this.menu = menu
        this.title = title
    }

    /** True while that menu is still the one the player has open. */
    fun title(): String? {
        val open = menu ?: return null
        if (Minecraft.getInstance().player?.containerMenu === open) return title
        menu = null
        title = null
        return null
    }
}
