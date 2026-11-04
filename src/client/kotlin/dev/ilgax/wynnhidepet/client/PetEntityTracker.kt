package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Display.TextDisplay
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds pets by their nametag's [pet] badge, then collects the rest of the pet from the entity IDs
 * Wynncraft allocated together with that nametag. Three layouts have been observed:
 *
 *   model before nametag:  root ItemDisplay R, its N ItemDisplay passengers, nametag (R+N+1), hitbox (R+N+2)
 *   model after nametag:   nametag, invisible ArmorStand / ItemDisplay parts (IDs may skip), hitbox
 *   ridden mob:            a mob (e.g. a creeper) carrying the nametag as a passenger
 *
 * Only entities inside those runs are collected, so mobs, NPCs or combat dummies standing next to a
 * pet can't be pulled in, no matter how close they are or when the client loaded them.
 */
object PetEntityTracker {

    val petEntityIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Names shown on pet nametags (the text before the first pill), used to recognise pet chat. */
    val knownPetNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val entityExpirations: MutableMap<Int, Long> = HashMap()
    private val newIds = HashSet<Int>()
    private var localTick = 0L

    // Nametag labels use Wynncraft's banner "pill" font: U+E060 opens a pill, U+E030..U+E049 are a..z
    // and U+E062 closes it. Every pet nametag carries an exact [pet] pill; mobs carry [lv] only.
    private const val PET_BADGE = "\uE060\uE03F\uE034\uE043\uE062"
    private val colorCodeRegex = Regex("§.")

    // Largest observed model is 37 parts; leave room for bigger pets.
    private const val MAX_MODEL_IDS = 128
    // The armor stand model skips every other ID.
    private const val MAX_ID_GAP = 3

    class PetAssembly(val ids: Set<Int>, val anchorId: Int, val reason: String)

    fun isPetNametag(text: String): Boolean {
        // Glyphs are separated by supplementary-plane spacer characters (e.g. U+CFFFF); drop them first.
        val clean = StringBuilder(text.length)
        text.replace(colorCodeRegex, "").codePoints().forEach { if (it <= 0xFFFF) clean.appendCodePoint(it) }
        return clean.contains(PET_BADGE)
    }

    fun findPets(level: ClientLevel, searchBox: AABB, allowUntagged: Boolean): List<PetAssembly> {
        val pets = ArrayList<PetAssembly>()

        for (tag in level.getEntitiesOfClass(TextDisplay::class.java, searchBox) { isPetNametag(it.text.string) }) {
            val name = tag.text.string.replace(colorCodeRegex, "").substringBefore('\uE060').trim()
            if (name.isNotEmpty()) knownPetNames.add(name)

            val ids = HashSet<Int>()
            ids.add(tag.id)
            val reason = when {
                collectRiddenMob(tag, ids) -> "nametag rides pet mob"
                collectModelBefore(level, tag.id, ids) -> {
                    (level.getEntity(tag.id + 1) as? Interaction)?.let { ids.add(it.id) }
                    "model before nametag"
                }
                collectModelAfter(level, tag.id, ids) -> "model after nametag"
                else -> "nametag only"
            }
            pets.add(PetAssembly(ids, tag.id, reason))
        }

        // Nametag not loaded (out of range, or lagging behind its hitbox): accept a hitbox only if the
        // model run directly precedes the missing nametag slot.
        if (allowUntagged) {
            for (hitbox in level.getEntitiesOfClass(Interaction::class.java, searchBox) { true }) {
                if (level.getEntity(hitbox.id - 1) != null) continue
                val ids = HashSet<Int>()
                if (!collectModelBefore(level, hitbox.id - 1, ids)) continue
                // Some world objects put their nametag *before* the model; such a model isn't a pet's.
                if (level.getEntity(ids.min() - 1) is TextDisplay) continue
                ids.add(hitbox.id)
                pets.add(PetAssembly(ids, hitbox.id, "hitbox after model, nametag not loaded"))
            }
        }

        return pets
    }

    /** The nametag rides the pet itself, e.g. the Veteran Creeper. */
    private fun collectRiddenMob(tag: TextDisplay, out: MutableSet<Int>): Boolean {
        val mob = tag.vehicle
        if (mob !is LivingEntity || mob is Player) return false
        addWithPassengers(mob, out)
        return true
    }

    /** Adds the ItemDisplay model ending right before [nametagId]; returns false if the layout doesn't match. */
    private fun collectModelBefore(level: ClientLevel, nametagId: Int, out: MutableSet<Int>): Boolean {
        val last = level.getEntity(nametagId - 1) ?: return false
        val root = last.vehicle ?: last
        if (root !is Display.ItemDisplay || root.vehicle != null) return false
        if (root.id + root.passengers.size + 1 != nametagId) return false
        if (root.passengers.any { it !is Display.ItemDisplay || it.id !in root.id + 1 until nametagId }) return false

        out.add(root.id)
        root.passengers.forEach { out.add(it.id) }
        return true
    }

    /**
     * Adds model parts following the nametag up to and including the first hitbox, e.g. the Heroes Guide.
     * Any other kind of entity in between means the run isn't this pet's, and nothing is added.
     */
    private fun collectModelAfter(level: ClientLevel, nametagId: Int, out: MutableSet<Int>): Boolean {
        val parts = ArrayList<Entity>()
        var gap = 0
        for (id in nametagId + 1..nametagId + MAX_MODEL_IDS) {
            val entity = level.getEntity(id)
            when {
                entity == null -> if (++gap > MAX_ID_GAP) return false
                entity is Interaction -> {
                    if (parts.isEmpty()) return false
                    parts.forEach { addWithPassengers(it, out) }
                    out.add(entity.id)
                    return true
                }
                entity is Display.ItemDisplay || (entity is ArmorStand && entity.isInvisible) -> {
                    parts.add(entity)
                    gap = 0
                }
                else -> return false
            }
        }
        return false
    }

    private fun addWithPassengers(entity: Entity, out: MutableSet<Int>) {
        if (entity is Player) return
        out.add(entity.id)
        entity.passengers.forEach { addWithPassengers(it, out) }
    }

    fun update(client: Minecraft) {
        val config = getConfig()
        localTick += config.updateFrequency.coerceAtLeast(1).toLong()
        // Sound and chat filtering need pet positions and names even while the pets themselves are shown.
        if (!config.hidePets && !config.hidePetSounds && !config.hidePetChat) {
            petEntityIds.clear()
            entityExpirations.clear()
            return
        }
        val level = client.level ?: return
        val player = client.player ?: return

        newIds.clear()
        val searchBox = player.boundingBox.inflate(config.searchRadius)
        for (pet in findPets(level, searchBox, config.enableLenientClustering)) {
            newIds.addAll(pet.ids)
        }

        // Remember detected IDs for a while so pets don't flicker when a nametag briefly drops out.
        for (id in newIds) {
            entityExpirations[id] = localTick + config.memoryDurationTicks
        }
        entityExpirations.entries.removeIf { it.value < localTick }

        // Sync the public set for the renderer and interaction mixins
        val currentKeys = entityExpirations.keys
        petEntityIds.removeIf { it !in currentKeys }
        petEntityIds.addAll(currentKeys)
    }

    fun reset() {
        petEntityIds.clear()
        entityExpirations.clear()
        newIds.clear()
        localTick = 0
    }
}
