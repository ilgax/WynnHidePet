package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity
import net.minecraft.entity.decoration.InteractionEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.passive.WanderingTraderEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.TypeFilter

object PetEntityTracker {

    val petEntityIds: MutableSet<Int> = HashSet()
    private val entityExpirations: MutableMap<Int, Int> = HashMap()
    private val newIds = HashSet<Int>()
    private var localTick = 0

    fun update(client: MinecraftClient) {
        localTick++
        if (!getConfig().hidePets) {
            petEntityIds.clear()
            entityExpirations.clear()
            return
        }
        val world = client.world ?: return
        val player = client.player ?: return

        newIds.clear()

        // Use a fixed 48-block radius.
        val searchBox = player.boundingBox.expand(48.0)
        val interactions = world.getEntitiesByType(
            TypeFilter.instanceOf(InteractionEntity::class.java), searchBox) { true }

        for (interaction in interactions) {
            // Pet interaction hitboxes (approx 0.6x0.9 up to 1.0x2.0 for vybels/moths)
            if (interaction.width < 0.2f || interaction.width > 1.2f) continue
            if (interaction.height < 0.4f || interaction.height > 2.5f) continue

            val interactionAge = interaction.age
            val clusterBox = interaction.boundingBox.expand(2.0, 5.0, 2.0)

            // Surgical search: Find potential components based on strict Age match (+/- 2 ticks).
            val nearby = world.getOtherEntities(interaction, clusterBox) { 
                it.id != interaction.id && 
                it !is PlayerEntity && 
                it !is InteractionEntity &&
                it !is VillagerEntity &&
                it !is WanderingTraderEntity &&
                kotlin.math.abs(it.age - interactionAge) <= 2
            }

            // Refine candidates into confirmed pet parts:
            // 1. Models/Shadows (ItemDisplay, Mob, ArmorStand) are hidden by Age alone.
            // 2. Nametags (TextDisplay) are ONLY hidden if they match Age AND have PUA symbols.
            val confirmedParts = nearby.filter {
                it !is TextDisplayEntity || it.text.string.contains("\uE060")
            }

            // A valid pet must have at least one component part confirmed
            if (confirmedParts.isEmpty()) continue

            // Confirmed pet — track the interaction and its specific components recursively.
            newIds.add(interaction.id)
            for (entity in confirmedParts) {
                addAllPassengersRecursive(entity, newIds)
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
        petEntityIds.clear()
        petEntityIds.addAll(entityExpirations.keys)
    }

    private fun addAllPassengersRecursive(entity: Entity, set: MutableSet<Int>) {
        if (entity is PlayerEntity || entity is VillagerEntity || entity is WanderingTraderEntity) return
        set.add(entity.id)
        for (passenger in entity.passengerList) {
            addAllPassengersRecursive(passenger, set)
        }
    }
}
