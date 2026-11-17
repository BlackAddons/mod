package org.blackaddons.api.hud

/** The parts vanilla draws on the HUD, as the loader names them. */
enum class VanillaHudPart {
    MISC_OVERLAYS,
    CROSSHAIR,
    SPECTATOR_MENU,
    HOTBAR,
    ARMOR,
    HEALTH,
    FOOD,
    AIR,
    MOUNT_HEALTH,

    /** Experience, locator or jump bar. */
    INFO_BAR,
    EXPERIENCE_LEVEL,
    HELD_ITEM_NAME,
    SPECTATOR_TOOLTIP,
    STATUS_EFFECTS,
    BOSS_BARS,
    SLEEP,
    DEMO_TIMER,
    SCOREBOARD,
    ACTION_BAR,
    TITLE,
    CHAT,
    PLAYER_LIST,
    SUBTITLES,
}

/**
 * The vanilla HUD around our own elements. For now it can only hide parts; moving or replacing them would also go
 * here.
 */
interface VanillaHud {
    /** Hides [part] while [hidden] returns true. Conditions from several features add up: any one of them hides it. */
    fun hideWhen(part: VanillaHudPart, hidden: () -> Boolean)
}
