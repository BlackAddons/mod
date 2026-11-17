package org.blackaddons.platform.impl

import net.minecraft.client.Minecraft
import org.blackaddons.api.host.LocaleHost

object MinecraftLocale : LocaleHost {
    // Same field on 26.3 and 26.2 (javap-checked), so no McCompat seam needed.
    override val current: String
        get() = Minecraft.getInstance().options.languageCode
}
