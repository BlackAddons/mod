package org.blackaddons.platform.ui.screen

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.core.ui.screen.ClickGuiScreen

class BlackaddonsModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            UiManager.pushSilently(ClickGuiScreen())
            SkijaHostScreen(parent)
        }
    }
}
