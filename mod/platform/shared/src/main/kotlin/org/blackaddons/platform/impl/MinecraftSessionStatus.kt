package org.blackaddons.platform.impl

import net.minecraft.client.Minecraft
import org.blackaddons.api.host.SessionOwnership
import org.blackaddons.api.host.SessionStatus
import org.blackaddons.core.backend.auth.SessionVerification
import java.util.UUID

/**
 * Read-only, addon-safe session view: identity and a host-mediated ownership check, never the token or handshake
 * ([MinecraftMojangSession]).
 */
object MinecraftSessionStatus : SessionStatus {
    private val mc get() = Minecraft.getInstance()

    override val username: String get() = mc.user.name
    override val uuid: UUID get() = mc.user.profileId

    override fun verifyOwnership(callback: (SessionOwnership) -> Unit) =
        SessionVerification.verifyAsync(callback)
}
