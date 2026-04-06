package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity
import net.minecraft.entity.decoration.InteractionEntity
import net.minecraft.util.TypeFilter

object PetEntityTracker {

    val petEntityIds: MutableSet<Int> = HashSet()
    private val newIds = HashSet<Int>()
    private var graceTicks = 0

    fun update(client: MinecraftClient) {
        if (!getConfig().hidePets) {
            petEntityIds.clear()
            graceTicks = 0
            return
        }
        val world = client.world ?: return
        val player = client.player ?: return

        newIds.clear()

        // Use the player's render distance as the search radius. Entities beyond loaded chunks
        // aren't tracked by the server anyway, and getEntitiesByType uses spatial indexing
        // which is faster than iterating all world entities on a large server like Wynncraft.
        val renderDistanceBlocks = client.options.viewDistance.value * 16.0
        val searchBox = player.boundingBox.expand(renderDistanceBlocks)
        val interactions = world.getEntitiesByType(
            TypeFilter.instanceOf(InteractionEntity::class.java), searchBox) { true }

        for (interaction in interactions) {
            val nearbyBox = interaction.boundingBox.expand(2.0)

            // Pick the closest anchor (by horizontal distance) so an enemy entity nearby
            // doesn't accidentally win over the pet's own anchor.
            // No passenger requirement — creeper/guide pets may have no ItemDisplayEntity passengers.
            val anchor = world.getEntitiesByType(
                TypeFilter.instanceOf(ItemDisplayEntity::class.java), nearbyBox) { true }
                .minByOrNull { val dx = it.x - interaction.x; val dz = it.z - interaction.z; dx*dx + dz*dz }
                ?: continue

            // Wynncraft spawns each pet cluster atomically with consecutive entity IDs.
            // The anchor and interaction are the outermost IDs in the cluster, so the
            // standalone shadow and nametag always fall within [anchor.id, interaction.id].
            // This excludes nearby enemy entities even when they're at the same position.
            val clusterMin = minOf(anchor.id, interaction.id)
            val clusterMax = maxOf(anchor.id, interaction.id)
            if (clusterMax - clusterMin > 50) continue // sanity check against bad data
            val clusterRange = clusterMin..clusterMax

            // Confirmed pet — add interaction entity, anchor, and all display-entity passengers
            newIds.add(interaction.id)
            newIds.add(anchor.id)
            anchor.passengerList.filterIsInstance<DisplayEntity>()
                .forEach { newIds.add(it.id) }

            // Standalone shadow: nearby + within cluster ID range
            world.getEntitiesByType(TypeFilter.instanceOf(ItemDisplayEntity::class.java), nearbyBox) { true }
                .filter { it.vehicle == null && it.passengerList.isEmpty() && it.id in clusterRange }
                .forEach { newIds.add(it.id) }

            // Pet nametag: standalone text_display with PUA surrogate chars + within cluster ID range.
            // NPC nametags have vehicle != null. Enemy health bars are in a different ID range.
            val tallBox = interaction.boundingBox.expand(2.0, 5.0, 2.0)
            world.getEntitiesByType(TypeFilter.instanceOf(TextDisplayEntity::class.java), tallBox) { true }
                .filter { it.vehicle == null && it.text.string.any { c -> c.isHighSurrogate() } && it.id in clusterRange }
                .forEach { newIds.add(it.id) }
        }

        when {
            newIds.isNotEmpty() -> {
                // Normal case: found pets this tick — add new IDs without removing old ones
                // to prevent flickering when detection temporarily misses entities during camera movement.
                petEntityIds.addAll(newIds)
                graceTicks = 60 // ~3 seconds of forgiveness for teleport lag
            }
            graceTicks > 0 -> {
                // Pet temporarily missing (teleporting to catch up after fast movement).
                // Keep the previous set until the grace period expires.
                graceTicks--
            }
            else -> {
                // Grace period expired with no pets detected — clear the set.
                petEntityIds.clear()
            }
        }
    }
}
