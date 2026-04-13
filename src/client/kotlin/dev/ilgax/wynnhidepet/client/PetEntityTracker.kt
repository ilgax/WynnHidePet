package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Display.TextDisplay
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object PetEntityTracker {

    val petEntityIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    
    
    private val entityExpirations: MutableMap<Int, Long> = HashMap()
    private val confirmedPetInteractionIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val newIds = HashSet<Int>()
    private var localTick = 0L

    // Strict matching: requires the explicit "Pet" tag sequence (U+E03F, U+E034, U+E043).
    // Strips Minecraft color codes first to handle colored signatures like §7§f§7.
    private val petSignatureRegex = Regex("\uE03F.{0,3}\uE034.{0,3}\uE043")
    private val colorCodeRegex = Regex("§.")

    private fun isStrictPetNametag(text: String): Boolean {
        val clean = text.replace(colorCodeRegex, "")
        return petSignatureRegex.containsMatchIn(clean)
    }

    // Lenient matching: used for entities that are part of a pet-like cluster.
    // Checks for specific symbols commonly used only in pets/mobs (E051+ range).
    private fun isLenientPetNametag(text: String): Boolean {
        if (isStrictPetNametag(text)) return true
        
        // Only allow multiline text to avoid hitting single-line quest NPC titles.
        if (!text.contains("\n")) return false
        
        val clean = text.replace(colorCodeRegex, "")
        for (c in clean) {
            // General range of pet icons (hearts, levels, stars)
            // Strictly exclude E055 (inspect icon used by NPCs) and E050.
            if (c in '\uE051'..'\uE054' || (c >= '\uE056' && c.code < 0xF8FF)) return true
        }
        return false
    }


    fun update(client: Minecraft) {
        val updateFreq = getConfig().updateFrequency.coerceAtLeast(1).toLong()
        localTick += updateFreq
        if (!getConfig().hidePets) {
            petEntityIds.clear()
            entityExpirations.clear()
            return
        }
        val level = client.level ?: return
        
        // In the Lobby/Start Screen, client.player might be null. 
        // We fallback to camera position if available.
        val searchPos: Vec3 = client.player?.position() 
            //?: client.gameRenderer.mainCamera.position
            ?: return

        newIds.clear()

        // Use configurable search radius.
        val config = getConfig()
        val r = config.searchRadius
        val searchBox = net.minecraft.world.phys.AABB(
            searchPos.x - r, searchPos.y - r, searchPos.z - r,
            searchPos.x + r, searchPos.y + r, searchPos.z + r
        )
        
        val interactions = level.getEntitiesOfClass(Interaction::class.java, searchBox) { true }

        for (interaction in interactions) {
            // Pet interaction hitboxes (approx 0.6x0.9 up to 1.0x2.0 for vybels/moths)
            if (interaction.bbWidth < 0.2f || interaction.bbWidth > 1.2f) continue
            if (interaction.bbHeight < 0.4f || interaction.bbHeight > 2.5f) continue

            val interactionAge = interaction.tickCount
            val clusterBox = interaction.boundingBox.inflate(2.0, 5.0, 2.0)

            // 1. Surgical search: Find potential components based on strict Age match (+/- 2 ticks).
            val nearby = level.getEntities(interaction, clusterBox) { 
                it.id != interaction.id && 
                it !is Player && 
                kotlin.math.abs(it.tickCount - interactionAge) <= config.clusterAgeTolerance
            }

            // Refine candidates into confirmed pet parts using signatures and icons.
            // Apply horizontal distance limit to avoid sucking in unrelated world displays (like rewards chests).
            val limitSq = config.clusterDistanceLimit * config.clusterDistanceLimit
            val confirmedParts = nearby.filter {
                val dx = it.x - interaction.x
                val dz = it.z - interaction.z
                val distSq = dx * dx + dz * dz
                
                val inDistance = !config.useClusterDistanceLimit || distSq <= limitSq
                inDistance && (it !is TextDisplay || isLenientPetNametag(it.text.string))
            }

            // A valid pet must have at least one component part that is a NAMETAG with a pet symbol.
            // EXCEPT in the Lobby, OR if it's an ID we've already confirmed earlier in this session.
            val isKnownPet = confirmedPetInteractionIds.contains(interaction.id)
            val hasPetSignature = confirmedParts.any { it is TextDisplay && isStrictPetNametag(it.text.string) }
            val hasItemDisplay = confirmedParts.any { it is Display.ItemDisplay }
            val inLobby = searchPos.x > 18000.0 && searchPos.z < 0.0
            
            if (hasPetSignature) {
                confirmedPetInteractionIds.add(interaction.id)
            }

            if (!inLobby && !hasPetSignature && !isKnownPet) {
                // Lenient clustering: Only allow if enabled AND it contains an ItemDisplay (modern pet model).
                // This protects Villagers/NPCs which lack ItemDisplays.
                if (!config.enableLenientClustering || !hasItemDisplay) continue
            } else if (confirmedParts.isEmpty() && !inLobby && !isKnownPet) {
                continue
            }

            // Confirmed pet — track the interaction and its specific components recursively.
            newIds.add(interaction.id)
            for (entity in confirmedParts) {
                addAllPassengersRecursive(entity, newIds)
            }
        }

        // 3. Fallback: Identify isolated pet nametags (Lobby or faded clusters)
        // MUST use strict matching here to avoid hiding dungeon mobs with complex health bars.
        val nametags = level.getEntitiesOfClass(TextDisplay::class.java, searchBox) { isStrictPetNametag(it.text.string) }
        for (tag in nametags) {
            if (newIds.contains(tag.id)) continue
            
            // Search upwards to find the root of the pet assembly.
            var root: Entity = tag
            while (root.vehicle != null && isSafeToHide(root.vehicle)) {
                root = root.vehicle!!
                newIds.add(root.id)
            }
            // Once we have the root, hide everything in that branch if it's not an NPC.
            if (isSafeToHide(root)) {
                newIds.add(tag.id)
                addAllPassengersRecursive(root, newIds)
            }
        }

        // Add detected IDs with configurable TTL
        for (id in newIds) {
            entityExpirations[id] = localTick + config.memoryDurationTicks
        }

        // Clean up expired IDs
        val iterator = entityExpirations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < localTick) {
                confirmedPetInteractionIds.remove(entry.key)
                iterator.remove()
            }
        }

        // Sync the public set for the renderer and interaction mixins
        val currentKeys = entityExpirations.keys
        petEntityIds.removeIf { it !in currentKeys }
        petEntityIds.addAll(currentKeys)
    }


    private fun isSafeToHide(entity: Entity?): Boolean {
        if (entity == null) return false
        return entity !is Player
    }

    private fun addAllPassengersRecursive(entity: Entity, set: MutableSet<Int>) {
        if (!isSafeToHide(entity)) return
        set.add(entity.id)
        for (passenger in entity.passengers) {
            addAllPassengersRecursive(passenger, set)
        }
    }

    fun reset() {
        petEntityIds.clear()
        entityExpirations.clear()
        confirmedPetInteractionIds.clear()
        newIds.clear()
        localTick = 0
    }
}
