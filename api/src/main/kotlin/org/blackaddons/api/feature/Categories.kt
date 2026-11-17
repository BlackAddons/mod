package org.blackaddons.api.feature

import org.blackaddons.api.ui.icon.LucideIcons

/**
 * Host categories. Sidebar order lives in `TabRegistry`. A feature lands in one by package
 * (`features.impl.<category>.X`) or by naming a [FeatureSection].
 */
object Categories {

    object Dungeons : FeatureCategory("dungeons", LucideIcons.dungeon) {
        val General = section("general", "General")
        val Floor7 = section("floor7", "Floor 7")
    }

    object Visual : FeatureCategory("visual", LucideIcons.eye) {
        val World = section("world", "World")
        val Self = section("self", "Self")
    }

    // Not "interface": the id is a package segment, and `interface` would need backticks.
    object Interface : FeatureCategory("ui", LucideIcons.appWindow, label = "Interface")

    object Chat : FeatureCategory("chat", LucideIcons.chat)

    object Client : FeatureCategory("client", LucideIcons.monitor) {
        val General = section("general", "General")

        /** How the window and the main menu look. The title screen lists these features as quick toggles. */
        val Menu = section("menu", "Main Menu")
    }

    object Cheats : FeatureCategory("cheats", LucideIcons.cheats, hideWhenEmpty = true) {
        val General = section("general", "General")
        val Dungeons = section("dungeons", "Dungeons")
        val Kuudra = section("kuudra", "Kuudra")
        val Camera = section("camera", "Camera")
    }

    object Settings : FeatureCategory("settings", LucideIcons.settings) {
        val General = section("general", "General")
        val Appearance = section("appearance", "Appearance")
    }

    object Dev : FeatureCategory("dev", LucideIcons.dev)

    /** Listing them here is what initializes the objects before [CategoryRegistry] resolves packages. */
    val builtIns: List<FeatureCategory> = listOf(
        Dungeons, Visual, Interface, Chat, Client, Cheats, Settings, Dev,
    )
}
