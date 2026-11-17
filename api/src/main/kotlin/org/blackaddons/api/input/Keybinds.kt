package org.blackaddons.api.input

/**
 * Keybinds in the vanilla Controls menu (use `KeybindSetting` for ClickGUI-only hotkeys). Lang entries for
 * both keys must exist in the addon's lang file.
 */
interface Keybinds {
    /**
     * Idempotent per [translationKey]; register during `onLoad()`.
     * @param translationKey display name lang key, e.g. `"key.myaddon.toggle"`.
     * @param defaultKey `Keys.*` code, `Keys.UNKNOWN` for unbound.
     * @param categoryKey category lang key; defaults to BlackAddons'.
     */
    fun register(
        translationKey: String,
        defaultKey: Int,
        categoryKey: String = "key.category.blackaddons.main",
    ): VanillaKeybind
}

/** Polled by the framework every tick. */
interface VanillaKeybind {
    /** True once per press; several presses in one tick come back as successive trues. */
    fun wasPressed(): Boolean

    val isPressed: Boolean
}
