package org.blackaddons.api.event.skyblock

import org.blackaddons.api.event.Event
import org.blackaddons.api.skyblock.player.InvincibilityItem
import org.blackaddons.api.skyblock.player.Pet
import org.blackaddons.api.skyblock.player.PetChangeCause
import org.blackaddons.api.skyblock.player.SkillGain

/** [item] saved the player. Its cooldown in `Invincibility` is already running. */
class InvincibilityProcEvent(val item: InvincibilityItem) : Event()

/** `Pets.current` changed to [pet], which is null when the pet was despawned. */
class PetChangeEvent(val pet: Pet?, val cause: PetChangeCause) : Event()

/** The action bar showed a skill xp gain. */
class SkillXpEvent(val gain: SkillGain) : Event()
