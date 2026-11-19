package org.blackaddons.platform.impl

import com.mojang.authlib.exceptions.ForcedUsernameChangeException
import com.mojang.authlib.exceptions.InsufficientPrivilegesException
import com.mojang.authlib.exceptions.InvalidCredentialsException
import com.mojang.authlib.exceptions.UserBannedException
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
        } catch (e: Exception) {
            when (e) {
                is InvalidCredentialsException,
                is UserBannedException,
                is InsufficientPrivilegesException,
                is ForcedUsernameChangeException,
                -> JoinResult.REJECTED
                else -> JoinResult.UNAVAILABLE
            }
        }
    }
}
