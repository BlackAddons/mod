package org.blackaddons.core.features.impl.ui.inventorybuttons

import com.google.gson.GsonBuilder
import org.blackaddons.api.Platform
import org.blackaddons.core.storage.GuardedFile
import java.util.UUID

internal class ButtonProfile(
    val id: String = DEFAULT_PROFILE_ID,
    var name: String = "Default",
    val buttons: MutableList<InventoryButton> = mutableListOf(),
)

internal const val DEFAULT_PROFILE_ID = "default"

internal object InventoryButtonStore {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val file by lazy { GuardedFile(Platform.configDir.resolve(FILE_NAME)) }

    private val saved: Saved by lazy(::read)

    val profiles: List<ButtonProfile> get() = saved.profiles

    val active: ButtonProfile
        get() = saved.profiles.firstOrNull { it.id == saved.activeProfile } ?: saved.profiles.first()

    val buttons: MutableList<InventoryButton> get() = active.buttons

    // The edit pencil, shared by every profile. Only its position and size are used.
    val pencil: InventoryButton get() = saved.pencil

    var snapToButtons: Boolean
        get() = saved.snapToButtons
        set(value) {
            saved.snapToButtons = value
        }

    fun save(): Boolean = file.write(gson.toJson(saved))

    fun restore(buttons: List<InventoryButton>, pencil: InventoryButton) {
        active.buttons.clear()
        active.buttons += buttons
        saved.pencil = pencil
    }

    fun select(id: String) {
        if (saved.profiles.any { it.id == id }) saved.activeProfile = id
    }

    fun create(name: String, buttons: List<InventoryButton>): ButtonProfile =
        ButtonProfile(UUID.randomUUID().toString(), name, buttons.toMutableList()).also {
            saved.profiles += it
            saved.activeProfile = it.id
        }

    fun rename(id: String, name: String) {
        saved.profiles.firstOrNull { it.id == id }?.name = name
    }

    fun delete(id: String) {
        if (saved.profiles.size <= 1) return
        saved.profiles.removeIf { it.id == id }
        if (saved.profiles.none { it.id == saved.activeProfile }) saved.activeProfile = saved.profiles.first().id
    }

    private fun read(): Saved {
        val text = file.read() ?: return Saved()
        val saved = runCatching { gson.fromJson(text, Saved::class.java) }.getOrNull()
        if (saved == null) file.refuseWrites("not an inventory button list")
        return saved?.also { if (it.profiles.isEmpty()) it.profiles += ButtonProfile() } ?: Saved()
    }

    private class Saved(
        val profiles: MutableList<ButtonProfile> = mutableListOf(ButtonProfile()),
        var activeProfile: String = DEFAULT_PROFILE_ID,
        var pencil: InventoryButton = defaultPencil(),
        var snapToButtons: Boolean = false,
    )

    private fun defaultPencil() = InventoryButton(x = PENCIL_GAP, anchorRight = true, width = PENCIL_SIZE, height = PENCIL_SIZE)

    private const val FILE_NAME = "inventory_buttons.json"
    const val PENCIL_SIZE = 12
    private const val PENCIL_GAP = 2
}
