package org.blackaddons.core.feature

import org.blackaddons.api.Addon
import org.blackaddons.api.AddonContext
import org.blackaddons.api.Assertion
import org.blackaddons.api.Platform
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.auth.IdpService
import java.nio.file.Files
import java.nio.file.Path

/** An addon plus the mod id from the loader. The addon declares [Addon.id] itself, [modId] decides identity. */
class AddonRegistration(val addon: Addon, val modId: String)

/** Given directly to one addon. Never registered anywhere it could be looked up. */
internal class HostAddonContext(override val addonId: String) : AddonContext {

    // Uses the loader's mod id, not the self-declared Addon.id, so no addon can take another addon's folder.
    override val dataDir: Path
        get() = Platform.configDir.resolve("addons").resolve(addonId).also { Files.createDirectories(it) }

    override fun requestAssertion(nonce: String?, onResult: (Assertion?) -> Unit) = OffThread.run(
        name = "idp-assertion",
        work = { IdpService.assertion(addonId, nonce) },
        onFailure = { null },
        onResult = { assertion -> Platform.screenHost.onClientThread { onResult(assertion) } },
    )
}
