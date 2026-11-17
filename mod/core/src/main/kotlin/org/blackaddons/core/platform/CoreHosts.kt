package org.blackaddons.core.platform

import org.blackaddons.api.config.ConfigHost
import org.blackaddons.api.host.AddonLoggers
import org.blackaddons.api.host.AddonStorages
import org.blackaddons.api.host.HttpHost
import org.blackaddons.api.host.MojangAccounts
import org.blackaddons.api.host.Scheduler
import org.blackaddons.api.skyblock.SkyblockLevelsProvider
import org.blackaddons.api.skyblock.profile.SkyblockProfiles
import org.blackaddons.core.backend.SharedHttp
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.log.Slf4jAddonLoggers
import org.blackaddons.core.profile.CoreMojangAccounts
import org.blackaddons.core.profile.CoreSkyblockLevels
import org.blackaddons.core.profile.CoreSkyblockProfiles
import org.blackaddons.core.scheduler.DefaultScheduler
import org.blackaddons.core.storage.FileAddonStorages

/** Facades core implements itself, typed as interfaces so the implementations stay internal. */
object CoreHosts {
    val scheduler: Scheduler = DefaultScheduler
    val loggers: AddonLoggers = Slf4jAddonLoggers
    val mojang: MojangAccounts = CoreMojangAccounts
    val skyblockLevels: SkyblockLevelsProvider = CoreSkyblockLevels
    val profiles: SkyblockProfiles = CoreSkyblockProfiles
    val storage: AddonStorages = FileAddonStorages
    val config: ConfigHost = BlackaddonsConfig
    val http: HttpHost = SharedHttp
}
