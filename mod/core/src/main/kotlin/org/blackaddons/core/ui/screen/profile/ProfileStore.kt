package org.blackaddons.core.ui.screen.profile

/** Named profiles with one active. [ProfileMenu] and [ProfilesModal] share it, so FME and Actions match. */
internal interface ProfileStore {

    data class Entry(val id: String, val name: String)

    val title: String
    val profiles: List<Entry>
    val active: Entry

    fun select(id: String)
    fun create(name: String, copyCurrent: Boolean)
    fun rename(id: String, name: String)
    fun delete(id: String)
    fun openFolder()

    fun export(): String?

    fun paste(text: String, applied: () -> Unit)
}
