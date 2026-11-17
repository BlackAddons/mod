package org.blackaddons.extras.core

import org.blackaddons.api.skyblock.profile.Skill

/** The vanilla item that matches each skill's Hypixel menu icon. */
internal val Skill.iconItem: String
    get() = "minecraft:" + when (this) {
        Skill.COMBAT -> "stone_sword"
        Skill.MINING -> "stone_pickaxe"
        Skill.FARMING -> "golden_hoe"
        Skill.FORAGING -> "jungle_sapling"
        Skill.FISHING -> "fishing_rod"
        Skill.ENCHANTING -> "enchanting_table"
        Skill.ALCHEMY -> "brewing_stand"
        Skill.TAMING -> "ghast_spawn_egg"
        Skill.HUNTING -> "lead"
        Skill.CARPENTRY -> "crafting_table"
        Skill.SOCIAL -> "emerald"
        Skill.RUNECRAFTING -> "magma_cream"
    }
