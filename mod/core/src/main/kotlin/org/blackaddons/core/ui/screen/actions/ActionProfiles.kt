package org.blackaddons.core.ui.screen.actions

import org.blackaddons.core.actions.ActionPortable
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.profile.ProfileStore

internal object ActionProfiles : ProfileStore {
    override val title: String get() = actionTr("profile.title", "Action Profiles")
    override val profiles: List<ProfileStore.Entry> get() = ActionStorage.manifest.profiles.map { ProfileStore.Entry(it.id, it.name) }
    override val active: ProfileStore.Entry get() = ActionStorage.activeProfile.let { ProfileStore.Entry(it.id, it.name) }

    override fun select(id: String) = ActionStorage.selectProfile(id)

    override fun create(name: String, copyCurrent: Boolean) {
        ActionStorage.createProfile(name, copyCurrent)
    }

    override fun rename(id: String, name: String) = ActionStorage.renameProfile(id, name)

    override fun delete(id: String) {
        ActionStorage.deleteProfile(id)
    }

    override fun openFolder() = ActionStorage.openFolder()

    override fun export(): String = ActionPortable.export(ActionStorage.activeProfile, asProfile = true)

    override fun paste(text: String, applied: () -> Unit) = PortableImports.open(ActionPortable, text) { applied() }
}
