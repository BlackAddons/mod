package org.blackaddons.core.features.impl.dev

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n

/** Dev tool: [CopyItemData] for the crosshair entity. */
internal object CopyEntityData : Feature(
    description = "Copy the data of the entity you are looking at to the clipboard.",
    name = "Copy Entity Data",
) {
    private val key by keybind("Copy Key")
        .withDescription("Press while looking at an entity to copy its data.")

    override fun init() {
        onKeybind(key) {
            val raw = Platform.interaction.crosshairEntityRaw() ?: return@onKeybind
            Platform.clipboard.copy(CopyItemData.pretty(raw))
            Toasts.show(I18n.trOr("blackaddons.copy_entity_data.copied", "Copied entity data to clipboard."), kind = ToastKind.SUCCESS)
        }
    }
}
