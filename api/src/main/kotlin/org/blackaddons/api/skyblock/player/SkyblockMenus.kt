package org.blackaddons.api.skyblock.player

import org.blackaddons.api.gui.task.ContainerTask
import org.blackaddons.api.gui.task.ContainerTaskOptions
import org.blackaddons.api.gui.task.ContainerTaskResult
import org.blackaddons.api.gui.task.containerTask
import org.blackaddons.api.world.ItemInfo

/** Container tasks for Hypixel's own menus. Run them with [ContainerTask.run]. */
object SkyblockMenus {

    const val EQUIPMENT_TITLE = "Stats & Equipment"
    val PETS_TITLE = Regex("""^(?:\(\d+/\d+\) )?Pets(?: \(\d+/\d+\))?$""")

    /** Lore of the summoned pet in the pets menu. */
    const val SUMMONED_PET_LORE = "Click to despawn!"

    private const val EQUIPMENT_COMMAND = "stats"
    private const val PETS_COMMAND = "petsmenu"
    private const val SUMMONABLE_PET_LORE = "Left-click to summon!"

    /** Shift-clicks the first inventory item that [item] accepts into its equipment slot. */
    fun equip(
        name: String,
        options: ContainerTaskOptions = ContainerTaskOptions(),
        onFinished: (ContainerTaskResult) -> Unit = {},
        item: (ItemInfo) -> Boolean,
    ): ContainerTask = containerTask(name, options) {
        open(EQUIPMENT_COMMAND, EQUIPMENT_TITLE)
        quickMoveFromInventory("Not in the inventory", item)
        catchReopen(EQUIPMENT_TITLE)
        onFinished(onFinished)
    }

    /**
     * Summons the first pet that [pet] accepts from the open page of the pets menu. A pet that is already out is left
     * alone. There is no paging yet, so a pet on a later page fails. Add paging through "Next Page" once someone needs
     * it.
     */
    fun summonPet(
        name: String,
        options: ContainerTaskOptions = ContainerTaskOptions(),
        onFinished: (ContainerTaskResult) -> Unit = {},
        pet: (Pet) -> Boolean,
    ): ContainerTask = containerTask(name, options) {
        open(PETS_COMMAND, PETS_TITLE)
        click("Not on the pets menu page", skipIf = { item -> item.loreLines.none { SUMMONABLE_PET_LORE in it } }) { item ->
            Pet.of(item)?.let(pet) == true
        }
        onFinished(onFinished)
    }
}
