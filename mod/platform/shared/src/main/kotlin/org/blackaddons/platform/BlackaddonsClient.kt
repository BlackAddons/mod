// Copyright (c) 2026 BlackAddons Team. All rights reserved.
// Licensed under the PolyForm Strict License 1.0.0 with additional permissions - see LICENSE.md and
// PERMISSIONS.md. Copying this code into other projects requires the BlackAddons Team's written permission.

package org.blackaddons.platform

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.blackaddons.api.Addon
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientStoppingEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.gui.task.ContainerTasks
import org.blackaddons.api.hypixel.Party
import org.blackaddons.api.input.Keys
import org.blackaddons.api.skyblock.Election
import org.blackaddons.api.skyblock.SkyblockLevels
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.player.Invincibility
import org.blackaddons.api.skyblock.player.Pets
import org.blackaddons.api.skyblock.player.SkyblockStats
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.backend.BackendDns
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.commands.BlackaddonsCommand
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.cosmetics.store.CosmeticOwners
import org.blackaddons.core.dungeon.CoreDungeon
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.map.RoomRegistry
import org.blackaddons.core.feature.AddonRegistration
import org.blackaddons.core.feature.FeatureLabels
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.gamedata.DungeonScoring
import org.blackaddons.core.gamedata.LevelingTables
import org.blackaddons.core.gamedata.SkillCaps
import org.blackaddons.core.gamedata.SkyblockAreas
import org.blackaddons.core.hypixel.CoreParty
import org.blackaddons.core.interaction.FishingTracker
import org.blackaddons.core.log.Log
import org.blackaddons.core.platform.CoreHosts
import org.blackaddons.core.profile.friends.FriendListWatcher
import org.blackaddons.core.scheduler.DefaultScheduler
import org.blackaddons.core.skyblock.CoreElection
import org.blackaddons.core.skyblock.InvincibilityTracker
import org.blackaddons.core.skyblock.LocationChanges
import org.blackaddons.core.skyblock.PetTracker
import org.blackaddons.core.skyblock.StatsTracker
import org.blackaddons.core.soloclears.RunRecorder
import org.blackaddons.core.ui.screen.ClickGuiScreen
import org.blackaddons.core.update.UpdateCheck
import org.blackaddons.platform.chat.ChatImagePeek
import org.blackaddons.platform.chat.ChatScreenEvents
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.cosmetics.Cosmetic3dManager
import org.blackaddons.platform.dungeon.map.DungeonScanner
import org.blackaddons.platform.dungeon.map.HotbarMapScanner
import org.blackaddons.platform.event.CustomPayloadEmitter
import org.blackaddons.platform.event.EventEmitters
import org.blackaddons.platform.event.impl.RenderScreenEvent
import org.blackaddons.platform.font.MinecraftGlobalFonts
import org.blackaddons.platform.hypixel.PartyInfoChannel
import org.blackaddons.platform.impl.chat.MinecraftChat
import org.blackaddons.platform.impl.gui.TitleMenu
import org.blackaddons.platform.impl.gui.TitleQuickToggles
import org.blackaddons.platform.ui.screen.ScreenOverlay
import org.blackaddons.platform.util.ClientTicks

class BlackaddonsClient : ClientModInitializer {

    override fun onInitializeClient() {
        // Before anything reads a TickCache, and before EventEmitters so Pre listeners don't read it a tick stale.
        ClientTicks.start()
        EventEmitters.init()
        MinecraftGlobalFonts.install()
        CustomPayloadEmitter.install()
        DefaultScheduler.startTicking()
        // Resolve early so a slow resolver (VPN) doesn't cost us when opening the Profile Viewer.
        BackendDns.init()
        Cosmetic3dManager.start()
        // The provider, not just the instance: the loader's answer to which jar declared it mints the identity.
        val addons = FabricLoader.getInstance()
            .getEntrypointContainers("blackaddons:addon", Addon::class.java)
            .mapNotNull { container ->
                // One failing addon shouldn't break startup for the rest.
                try {
                    AddonRegistration(container.entrypoint, container.provider.metadata.id)
                } catch (t: Throwable) {
                    Log.main.error("Addon from mod '${container.provider.metadata.id}' failed to construct - skipping it", t)
                    null
                }
            }
        // Before any addon code runs, because feature objects, onLoad and onEnable might already read these facades.
        Dungeon.bind(CoreDungeon)
        SkyblockLevels.bind(CoreHosts.skyblockLevels)
        Invincibility.bind(InvincibilityTracker)
        Pets.bind(PetTracker)
        Election.bind(CoreElection)
        Party.bind(CoreParty)
        SkyblockStats.bind(StatsTracker)
        ActionExecutor.bindQueue()
        FeatureManager.init(addons)
        // Between read and activation: addons see settings, and no onEnable has run yet.
        BlackaddonsConfig.read()
        FeatureManager.loadAddons()
        FeatureManager.activate()
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            EventBus.post(ClientStoppingEvent)
            BlackaddonsConfig.save()
            MinecraftChat.saveForRestart()
        }
        ActionExecutor.init()

        // Here so it exists no matter what state any feature is in.
        BlackaddonsCommand.register()

        // Feeds `/blackaddons pv` suggestions from chat, without sending commands.
        FriendListWatcher.start()

        DungeonTracker.start()
        InvincibilityTracker.start()
        PetTracker.start()
        CoreElection.start()
        PartyInfoChannel.install()
        StatsTracker.start()
        FishingTracker.start()
        LocationChanges.start()
        ContainerTasks.install()

        // Solo F7/M7 on a server-witnessed clock. Always on (gated by Solo Clears), recording starts at Mort's line.
        RunRecorder.start()

        DungeonScanner.init()
        HotbarMapScanner.init()

        // Toasts over open screens, where the HUD pass is hidden.
        EventListener(RenderScreenEvent::class.java, EventPriority.NORMAL) { Toasts.render() }.register()

        // Minecraft's hover can't carry a picture.
        ChatImagePeek.start()
        ChatScreenEvents.start()
        TitleMenu.install()
        TitleQuickToggles.install()
        ScreenOverlay.start()

        UpdateCheck.start()

        // Backend island names over the compiled ones, disk copy first.
        SkyblockAreas.load()

        // Same for skill caps: a cap bump is just a document edit.
        SkillCaps.load()
        LevelingTables.load()

        // Lets us flag a feature the day an update breaks it.
        FeatureLabels.load()

        // Served: the solo clears backend replays runs on the same document, so a one-sided edit rejects honest clears.
        DungeonScoring.load()

        // Replaces the bundled rooms as one validated snapshot. Bundled stays if it's missing or incompatible.
        RoomRegistry.load()

        // One push socket for cosmetics and notices, follows the player's Live Updates switch.
        Gateway.start()

        // Hypixel names people we have no entry for, so we need the owner directory to recognize them.
        CosmeticOwners.load()

        val openClickGui = Platform.keybinds.register("key.blackaddons.open_gui", Keys.RIGHT_SHIFT)
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openClickGui.wasPressed()) {
                if (client.currentScreen == null) ClickGuiScreen.open()
            }
        }

        logStartup()
    }

    private fun logStartup() {
        val addons = FeatureManager.loadedAddons
        Log.main.info(
            "BlackAddons {} on Minecraft {} - {} features, {} addons{}",
            Platform.blackaddonsVersion,
            Platform.minecraftVersion,
            FeatureManager.features.size,
            addons.size,
            if (addons.isEmpty()) "" else " (${addons.joinToString()})",
        )
    }
}
