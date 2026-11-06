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

    // Nametag labels use Wynncraft's banner "pill" font: U+E060 opens a pill, U+E030..U+E049 are a..z
    // and U+E062 closes it. Every pet nametag carries an exact [pet] pill; mobs carry [lv] only.
    private const val PET_BADGE = "\uE060\uE03F\uE034\uE043\uE062"
    private val colorCodeRegex = Regex("§.")

    // Largest observed model is 37 parts; leave room for bigger pets.
    private const val MAX_MODEL_IDS = 128
    // The armor stand model skips every other ID.
    private const val MAX_ID_GAP = 3

    class PetAssembly(val ids: Set<Int>, val anchorId: Int, val reason: String)

    fun isPetNametag(text: String): Boolean = stripSpacers(text).contains(PET_BADGE)

    /** Glyphs are separated by supplementary-plane spacer characters (e.g. U+CFFFF); drop them and § codes. */
    private fun stripSpacers(text: String): String {
        val clean = StringBuilder(text.length)
        text.replace(colorCodeRegex, "").codePoints().forEach { if (it <= 0xFFFF) clean.appendCodePoint(it) }
        return clean.toString()
    }

    /** All loaded entities of [type], or only those inside [box] when given (debug captures). */
    private fun <T : Entity> entitiesOf(level: ClientLevel, type: Class<T>, box: AABB?): List<T> =
        if (box != null) level.getEntitiesOfClass(type, box) { true }
        else level.entitiesForRendering().filterIsInstance(type)

    /**
     * @param searchBox null scans every loaded entity, so pets are hidden at any render distance.
     */
    fun findPets(level: ClientLevel, searchBox: AABB?): List<PetAssembly> {
        val pets = ArrayList<PetAssembly>()

        for (tag in entitiesOf(level, TextDisplay::class.java, searchBox)) {
            if (!isPetNametag(tag.text.string)) continue

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

    /**
     * Periodic scan interval. New pets are caught by spawn-triggered scans and stay hidden until unload,
     * so this is only a safety net for nametag text that arrives unusually late.
     */
    const val SCAN_INTERVAL_TICKS = 10

    // A spawn schedules a scan at the end of that tick and one more on the next tick: nametag text comes in
    // a data packet after the spawn, and may land a tick later than the entity itself.
    private const val SPAWN_SCAN_TICKS = 2

    @Volatile
    private var spawnScansLeft = 0

    /** Called when a text display or interaction spawns. */
    fun requestScan() {
        spawnScansLeft = SPAWN_SCAN_TICKS
    }

    /** Once per tick: true while a spawn-triggered scan is still due. */
    fun consumeSpawnScan(): Boolean {
        if (spawnScansLeft <= 0) return false
        spawnScansLeft--
        return true
    }

    /**
     * Membership is sticky: once an entity is recognised as part of a pet it stays hidden until it
     * unloads or the world changes, so a nametag that briefly fails to match can't make a pet flicker.
     * The server can't reuse an entity ID while that entity is still loaded, so this never leaks.
     */
    fun update(client: Minecraft) {
        val config = getConfig()
        // Sound and chat filtering need pet positions and names even while the pets themselves are shown.
        if (!config.hidePets && !config.hidePetSounds && !config.hidePetChat) {
            reset()
            return
        }
        val level = client.level ?: return
        if (client.player == null) return

        for (pet in findPets(level, null)) {
            petEntityIds.addAll(pet.ids)
        }
        // Backstop in case an unload event was missed.
        petEntityIds.removeIf { level.getEntity(it) == null }
    }

    /** Called from the entity unload event; the ID may be reused by an unrelated entity afterwards. */
    fun onEntityUnload(id: Int) {
        petEntityIds.remove(id)
    }

    fun reset() {
        petEntityIds.clear()
    }
}
