package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.features.impl.visual.fme.Fme
import org.blackaddons.core.features.impl.visual.fme.FmePortable
import org.blackaddons.core.ui.screen.profile.ProfileStore

internal object FmeProfiles : ProfileStore {
    override val title: String get() = I18n.trOr("blackaddons.fme.profile_title", "FME Profiles")
    override val profiles: List<ProfileStore.Entry> get() = Fme.profiles().map { ProfileStore.Entry(it.id, it.name) }
    override val active: ProfileStore.Entry get() = Fme.activeProfile().let { ProfileStore.Entry(it.id, it.name) }

    override fun select(id: String) {
        Fme.selectProfile(id)
    }

    override fun create(name: String, copyCurrent: Boolean) {
        Fme.createProfile(name, copyCurrent)
    }

    override fun rename(id: String, name: String) {
        Fme.renameProfile(id, name)
    }

    override fun delete(id: String) {
        Fme.deleteProfile(id)
    }

    override fun openFolder() = Fme.openProfileFolder()

    override fun export(): String? = FmePortable.export()

    override fun paste(text: String, applied: () -> Unit) = PortableImports.open(FmePortable, text) { applied() }
}
