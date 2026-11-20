package org.blackaddons.core.features.impl.ui.inventorybuttons

import com.google.gson.GsonBuilder
import org.blackaddons.api.Platform
import org.blackaddons.core.storage.GuardedFile

internal object InventoryButtonStore {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val file by lazy { GuardedFile(Platform.configDir.resolve("inventory_buttons.json")) }

    private val saved: Saved by lazy(::read)

    val buttons: MutableList<InventoryButton> get() = saved.buttons

    // The edit pencil. Only its position and size are used.
    val pencil: InventoryButton get() = saved.pencil

    fun save() {
        file.write(gson.toJson(saved))
    }

    private fun read(): Saved {
        val text = file.read() ?: return Saved()
        val saved = runCatching { gson.fromJson(text, Saved::class.java) }.getOrNull()
        if (saved == null) file.refuseWrites("not an inventory button list")
        return saved ?: Saved()
    }

    private class Saved(
        val buttons: MutableList<InventoryButton> = mutableListOf(),
        val pencil: InventoryButton = defaultPencil(),
    )

    private fun defaultPencil() = InventoryButton(x = PENCIL_GAP, anchorRight = true, width = PENCIL_SIZE, height = PENCIL_SIZE)

    const val PENCIL_SIZE = 12
    private const val PENCIL_GAP = 2
}
