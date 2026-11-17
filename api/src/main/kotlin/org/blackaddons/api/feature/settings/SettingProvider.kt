package org.blackaddons.api.feature.settings

import org.blackaddons.api.feature.settings.impl.*
import org.blackaddons.api.input.InputType
import org.blackaddons.api.input.Keys
import java.awt.Color
import kotlin.reflect.KProperty

/**
 * Setting DSL available inside a feature:
 * ```
 * val range by number("Range", 4.0, 1.0, 8.0).withDescription("How far to reach.")
 * ```
 */
interface SettingProvider {

    /** Called by `by` for every declared setting. */
    fun registerSetting(setting: Setting<*>)

    operator fun <T, S : Setting<T>> S.provideDelegate(thisRef: SettingProvider, prop: KProperty<*>): S =
        also { registerSetting(it) }

    operator fun <T, S : Setting<T>> S.getValue(thisRef: SettingProvider, prop: KProperty<*>): S = this

    fun boolean(name: String, default: Boolean = false, id: String = name) =
        BooleanSetting(name, default, id)

    fun number(
        name: String,
        default: Double,
        min: Double,
        max: Double,
        step: Double = 1.0,
        unit: String = "",
        id: String = name,
    ) = NumberSetting(name, default, min, max, step, unit, id)

    fun color(name: String, default: Color, allowAlpha: Boolean = true, id: String = name, textChroma: Boolean = false) =
        ColorSetting(name, default, allowAlpha, id, textChroma)

    fun mode(name: String, default: String, modes: List<String>, id: String = name) =
        ModeSetting(name, default, modes, id)

    fun string(
        name: String,
        default: String,
        placeholder: String = "",
        maxLength: Int = 64,
        id: String = name,
        isMasked: Boolean = false,
        accepts: (String) -> Boolean = { true },
    ) = StringSetting(name, default, placeholder, maxLength, id, isMasked, accepts)

    fun keybind(
        name: String,
        default: Int = Keys.UNKNOWN,
        defaultType: InputType = InputType.KEYBOARD,
        id: String = name,
    ) = KeybindSetting(name, default, defaultType, id)

    fun button(name: String, action: () -> Unit, label: String = name, id: String = name) =
        ButtonSetting(name, action, label, id)

    fun list(
        name: String,
        default: List<String> = emptyList(),
        placeholder: String = "",
        itemMaxLength: Int = 64,
        id: String = name,
    ) = ListSetting(name, default, placeholder, itemMaxLength, id)

    /** Every entry gets its own choice of each of [options], picked on its row in the editor. */
    fun <S : ListSetting> S.withEntryOptions(vararg options: EntryOption): S = apply { entryOptions = options.toList() }

    /** All of [options], in an order the player sets by dragging. The first one comes first. */
    fun order(name: String, options: List<String>, id: String = name) = OrderSetting(name, options, id)

    fun <S : Setting<*>> S.withDescription(text: String): S = apply {
        description = if (text.endsWith('.')) text else "$text."
    }

    /** Settings sharing [label] render in one collapsible subsection. */
    fun <S : Setting<*>> S.group(label: String): S = apply { group = label }

    fun <S : Setting<*>> S.showIf(condition: () -> Boolean): S = apply { visibility = condition }

    fun <S : Setting<*>> S.hideIf(condition: () -> Boolean): S = apply { visibility = { !condition() } }

    /** Runs [listener] after each write that changes the value, including config loads and resets. */
    fun <T, S : Setting<T>> S.onChange(listener: (T) -> Unit): S = apply { addChangeListener(listener) }

    /**
     * Shows the setting as a box that opens its options as a list. Use it when there are too many modes to cycle
     * through comfortably.
     */
    fun <S : ModeSetting> S.asDropdown(): S = apply { isDropdown = true }

    /** Saved but never shown; edited by a screen of its own. */
    fun <S : Setting<*>> S.hidden(): S = apply { isHidden = true }

    /** Values matching [predicate] get the [label] badge, danger color and [reason] on hover. */
    fun <T, S : Setting<T>> S.unsafeWhen(label: String, reason: String, predicate: (T) -> Boolean): S = apply {
        unsafeLabel = label
        unsafeReason = if (reason.endsWith('.')) reason else "$reason."
        unsafeCheck = predicate
    }

    /** Everything outside [safe] is risky, e.g. `safeRange(6.0..20.0, ...)`. */
    fun <S : Setting<Double>> S.safeRange(safe: ClosedFloatingPointRange<Double>, label: String, reason: String): S =
        unsafeWhen(label, reason) { it !in safe }
}
