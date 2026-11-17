package org.blackaddons.core.skyblock

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.skyblock.PetChangeEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.ClickKind
import org.blackaddons.api.event.world.InventoryEvent
import org.blackaddons.api.skyblock.player.Pet
import org.blackaddons.api.skyblock.player.PetChangeCause
import org.blackaddons.api.skyblock.player.PetProvider
import org.blackaddons.api.skyblock.player.SkyblockMenus
import org.blackaddons.core.storage.GuardedFile

/**
 * The summoned pet, from summon, Autopet and level-up lines, corrected by the pets menu, which also tells us the pets'
 * uuids. Kept per profile and saved, so we still know it after a restart.
 */
object PetTracker : PetProvider {

    private val SUMMON = Regex("""^You (summoned|despawned) your (.+?)(?: ✦)?!$""")
    private val AUTOPET = Regex("""^Autopet equipped your \[Lvl (\d+)] (.+?)(?: ✦)?! VIEW RULE$""")
    private val LEVEL_UP = Regex("""^Your (.+?) leveled up to level (\d+)!$""")

    private val file by lazy { GuardedFile(Platform.configDir.resolve("pets.json")) }
    private val byProfile by lazy(::read)
    private val known = HashMap<String, Pet>()
    private var menuPets = emptyMap<Int, Pet>()
    private var lastMenu: List<String?>? = null
    private var clicked: Pet? = null
    private var lastProfile: String? = null

    fun start() {
        EventBus.subscribe<ChatReceiveEvent> { onChat(event.unformatted) }
        EventBus.subscribe<ClientTickEvent.Post> { scanMenu() }
        EventBus.subscribe<InventoryEvent.SlotClick> {
            if (event.kind == ClickKind.PICKUP && event.button == 0) clicked = menuPets[event.slotId]
        }
        EventBus.subscribe<WorldChangeEvent> {
            menuPets = emptyMap()
            lastMenu = null
            clicked = null
        }
    }

    override val current: Pet? get() = profileKey()?.let(byProfile::get)

    internal fun onChat(unformatted: String) {
        SUMMON.matchEntire(unformatted)?.destructured?.let { (action, name) ->
            val pet = if (action == "summoned") clicked?.takeIf { it.matches(name) } ?: resolve(name, level = null) else null
            clicked = null
            return set(pet, if (pet == null) PetChangeCause.DESPAWN else PetChangeCause.SUMMON)
        }
        AUTOPET.matchEntire(unformatted)?.destructured?.let { (level, name) ->
            clicked = null
            return set(resolve(name, level.toIntOrNull()), PetChangeCause.AUTOPET)
        }
        LEVEL_UP.matchEntire(unformatted)?.destructured?.let { (name, level) ->
            val pet = current?.takeIf { it.matches(name) } ?: return
            save(pet.copy(level = level.toIntOrNull() ?: pet.level))
        }
    }

    private fun scanMenu() {
        if (Platform.container.title()?.let(SkyblockMenus.PETS_TITLE::matches) != true) {
            menuPets = emptyMap()
            lastMenu = null
            return
        }
        val raw = Platform.container.capture()?.raw ?: return
        if (raw == lastMenu) return
        lastMenu = raw
        val pets = HashMap<Int, Pet>()
        var summoned: Pet? = null
        raw.forEachIndexed { slot, item ->
            val info = item?.let(Platform.items::describe) ?: return@forEachIndexed
            val pet = Pet.of(info) ?: return@forEachIndexed
            pets[slot] = pet
            pet.uuid?.let { known[it] = pet }
            if (info.loreLines.any { SkyblockMenus.SUMMONED_PET_LORE in it }) summoned = pet
        }
        menuPets = pets
        summoned?.let(::reconcile)
    }

    private fun reconcile(summoned: Pet) {
        if (clicked != null) return
        val tracked = current
        when {
            tracked == summoned -> Unit
            tracked != null && tracked.sameAs(summoned) -> save(summoned)
            else -> set(summoned, PetChangeCause.MENU)
        }
    }

    private fun resolve(name: String, level: Int?): Pet {
        val base = known.values.singleOrNull { Pet.normalize(it.name) == Pet.normalize(name) }
        return Pet(base?.name ?: name, level ?: base?.level, base?.uuid)
    }

    private fun set(pet: Pet?, cause: PetChangeCause) {
        if (save(pet)) EventBus.post(PetChangeEvent(pet, cause))
    }

    private fun save(pet: Pet?): Boolean {
        val key = profileKey() ?: return false
        if (pet == null) byProfile.remove(key) else byProfile[key] = pet
        file.write(JsonObject().apply {
            byProfile.forEach { (profile, saved) ->
                add(profile, JsonObject().apply {
                    addProperty("name", saved.name)
                    saved.level?.let { addProperty("level", it) }
                    saved.uuid?.let { addProperty("uuid", it) }
                })
            }
        }.toString())
        return true
    }

    private fun read(): HashMap<String, Pet> {
        val pets = HashMap<String, Pet>()
        val text = file.read() ?: return pets
        runCatching {
            JsonParser.parseString(text).asJsonObject.entrySet().forEach { (profile, json) ->
                val pet = json.asJsonObject
                pets[profile] = Pet(pet.get("name").asString, pet.get("level")?.asInt, pet.get("uuid")?.asString)
            }
        }.onFailure {
            file.refuseWrites("not a profile -> pet map")
            pets.clear()
        }
        return pets
    }

    private fun profileKey(): String? {
        val profile = Platform.skyblock.profile
        val account = Platform.skyblock.accountId
        if (profile != null && account != null) lastProfile = "$account:$profile"
        return lastProfile
    }
}
