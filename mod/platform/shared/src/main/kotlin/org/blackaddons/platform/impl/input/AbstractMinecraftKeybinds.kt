package org.blackaddons.platform.impl.input
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.blackaddons.api.input.Keybinds
import org.blackaddons.api.input.Keys
import org.blackaddons.api.input.VanillaKeybind
import org.blackaddons.platform.compat.NativeKeys

/**
 * [Keybinds] via Fabric, each registration is a `KeyMapping` in vanilla Controls. `key.category.<namespace>.<path>`
 * maps to `<namespace>:<path>`. Re-registering a [translationKey] returns the existing [VanillaKeybind].
 */
abstract class AbstractMinecraftKeybinds : Keybinds {

    private val cache = mutableMapOf<String, VanillaKeybindImpl>()

    protected abstract fun registerMapping(mapping: KeyMapping)

    // Check-then-create would race.
    @Synchronized
    override fun register(
        translationKey: String,
        defaultKey: Int,
        categoryKey: String,
    ): VanillaKeybind {
        cache[translationKey]?.let { return it }

        val mapping = KeyMapping(
            translationKey,
            NativeKeys.KEYBOARD,
            if (defaultKey == Keys.UNKNOWN) InputConstants.UNKNOWN.value else defaultKey,
            KeyMapping.Category(parseCategory(categoryKey)),
        )
        registerMapping(mapping)
        val handle = VanillaKeybindImpl(mapping)
        cache[translationKey] = handle
        return handle
    }

    private fun parseCategory(categoryKey: String): Identifier {
        val tail = categoryKey.removePrefix("key.category.")
        val dot = tail.indexOf('.')
        require(dot > 0 && dot < tail.length - 1) {
            "categoryKey must be 'key.category.<namespace>.<path>', got: $categoryKey"
        }
        val namespace = tail.substring(0, dot)
        val path = tail.substring(dot + 1).replace('.', '_')
        return Identifier.fromNamespaceAndPath(namespace, path)
    }

    private class VanillaKeybindImpl(val mapping: KeyMapping) : VanillaKeybind {
        override fun wasPressed(): Boolean = mapping.consumeClick()
        override val isPressed: Boolean get() = mapping.isDown
    }
}
