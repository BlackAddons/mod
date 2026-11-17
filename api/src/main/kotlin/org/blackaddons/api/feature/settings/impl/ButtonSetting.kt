package org.blackaddons.api.feature.settings.impl

import org.blackaddons.api.feature.settings.Setting

/** Not persisted. [label] is the word on the button - keep it a short verb ("Open", "Clear"); defaults to [name]. */
open class ButtonSetting(
    name: String,
    val action: () -> Unit,
    val label: String = name,
    id: String = name,
) : Setting<Unit>(id, name, Unit)
