package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display.TextDisplay
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object PetEntityTracker {

    val petEntityIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val entityExpirations: MutableMap<Int, Long> = HashMap()
    private val newIds = HashSet<Int>()
    private var localTick = 0L

    // Helper to check if a nametag contains pet-specific icons or text.
    private fun isPetNametag(text: String): Boolean {
        // Strict check: The "Pet" tag sequence (U+E03F, U+E034, U+E043) found in pet nametags.
        if (text.contains("\uE03F\uE034\uE043")) return true
        
        // Fallback for some pets that might use other symbols, 
        // but strictly EXCLUDE symbols used by NPCs like E055 (inspect) or E050.
        // Also require it to be multiline to avoid hitting single-line NPC titles.
        if (text.contains("\n")) {
            for (c in text) {
                if (c in '\uE051'..'\uE054' || c in '\uE056'..'\uE05F') return true
            }
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

        // Use a fixed 48-block radius around the player/camera.
        val searchBox = net.minecraft.world.phys.AABB(
            searchPos.x - 48.0, searchPos.y - 48.0, searchPos.z - 48.0,
            searchPos.x + 48.0, searchPos.y + 48.0, searchPos.z + 48.0
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
                kotlin.math.abs(it.tickCount - interactionAge) <= 2
            }

            // Refine candidates into confirmed pet parts:
            val confirmedParts = nearby.filter {
                it !is TextDisplay || isPetNametag(it.text.string)
            }

            // A valid pet must have at least one component part that is a NAMETAG with a pet symbol.
            // EXCEPT in the Lobby, where pets don't have nametags. Wynncraft Lobby is at X: 18370, Z: -880
            val inLobby = searchPos.x > 18000.0 && searchPos.z < 0.0
            
            if (!inLobby) {
                if (confirmedParts.none { it is TextDisplay && isPetNametag(it.text.string) }) continue
            } else {
                if (confirmedParts.isEmpty()) continue
            }

            // Confirmed pet — track the interaction and its specific components recursively.
            newIds.add(interaction.id)
            for (entity in confirmedParts) {
                addAllPassengersRecursive(entity, newIds)
            }
        }

        // 3. Fallback: Identify isolated pet nametags (e.g. in Lobby)
        val nametags = level.getEntitiesOfClass(TextDisplay::class.java, searchBox) { isPetNametag(it.text.string) }
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

        // Add detected IDs with a 60-tick (3s) TTL to prevent flickering
        for (id in newIds) {
            entityExpirations[id] = localTick + 60
        }

        // Clean up expired IDs
        val iterator = entityExpirations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < localTick) {
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
        newIds.clear()
        localTick = 0
    }
}
