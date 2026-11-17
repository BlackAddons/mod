package org.blackaddons.api.feature

/**
 * State kept outside settings (e.g. its own file) that a feature reset must also clear. Implementing it
 * enables reset even with no saved settings.
 */
interface OwnState {
    fun resetOwnState()
}
