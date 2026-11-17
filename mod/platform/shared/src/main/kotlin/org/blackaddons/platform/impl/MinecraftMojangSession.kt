package org.blackaddons.platform.impl

import com.mojang.authlib.exceptions.AuthenticationUnavailableException
import net.minecraft.client.Minecraft
import org.blackaddons.core.backend.auth.JoinResult
import org.blackaddons.core.backend.auth.MojangSession
import org.blackaddons.platform.compat.joinSessionServer
import java.util.UUID

object MinecraftMojangSession : MojangSession {

    private val mc get() = Minecraft.getInstance()

    override val username: String get() = mc.user.name
    override val uuid: UUID get() = mc.user.profileId

    override fun joinServer(serverId: String): JoinResult {
        return try {
            mc.joinSessionServer(serverId)
            JoinResult.JOINED
        } catch (e: AuthenticationUnavailableException) {
            // Session service down: ownership unknown.
            JoinResult.UNAVAILABLE
        } catch (e: Exception) {
            // Rejected: offline/cracked account.
            JoinResult.REJECTED
        }
    }
}
