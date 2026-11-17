// Copyright (c) 2026 BlackAddons Team. All rights reserved.
// Licensed under the PolyForm Strict License 1.0.0 with additional permissions - see LICENSE.md and
// PERMISSIONS.md. Copying this code into other projects requires the BlackAddons Team's written permission.

package org.blackaddons.platform

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.blackaddons.api.Platform
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.dev.DevFlags
import org.blackaddons.core.backend.auth.HostMojangSession
import org.blackaddons.core.profile.HostGameProfiles
import org.blackaddons.platform.impl.MinecraftGameProfiles
import org.blackaddons.platform.impl.MinecraftMojangSession

class Blackaddons : ModInitializer {

    companion object {
        const val MOD_ID = "blackaddons"
    }

    override fun onInitialize() {
        DevFlags.isDevelopment = FabricLoader.getInstance().isDevelopmentEnvironment
        // Each version module names its provider MinecraftPlatform, binding at compile time.
        Platform.bind(MinecraftPlatform)
        MinecraftPlatform.install()

        // After bind (it asks which language is selected), before anything reads a string.
        I18n.register(MOD_ID)

        // The privileged Mojang session isn't a facade, addons never see it.
        HostMojangSession.session = MinecraftMojangSession
        HostGameProfiles.lookup = MinecraftGameProfiles
    }
}
