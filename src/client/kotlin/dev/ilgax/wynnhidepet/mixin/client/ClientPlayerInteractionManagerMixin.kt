package dev.ilgax.wynnhidepet.mixin.client

import dev.ilgax.wynnhidepet.client.PetEntityTracker
import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.entity.Entity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ClientPlayerInteractionManager::class)
class ClientPlayerInteractionManagerMixin {

    @Inject(method = ["attackEntity"], at = [At("HEAD")], cancellable = true)
    fun onAttackEntity(
        player: PlayerEntity,
        entity: Entity,
        ci: CallbackInfo
    ) {
        if (getConfig().hidePets && entity.id in PetEntityTracker.petEntityIds) {
            ci.cancel()
        }
    }

    @Inject(method = ["interactEntity"], at = [At("HEAD")], cancellable = true)
    fun onInteractEntity(
        player: PlayerEntity,
        entity: Entity,
        hand: Hand,
        cir: CallbackInfoReturnable<ActionResult>
    ) {
        if (!getConfig().hidePets || entity.id !in PetEntityTracker.petEntityIds) return
        val behindEntity = findEntityBehindPets(player) ?: run { cir.returnValue = ActionResult.PASS; return }
        MinecraftClient.getInstance().interactionManager?.interactEntity(player, behindEntity, hand)
        cir.returnValue = ActionResult.SUCCESS
    }

    @Inject(method = ["interactEntityAtLocation"], at = [At("HEAD")], cancellable = true)
    fun onInteractEntityAtLocation(
        player: PlayerEntity,
        entity: Entity,
        hitResult: EntityHitResult,
        hand: Hand,
        cir: CallbackInfoReturnable<ActionResult>
    ) {
        if (!getConfig().hidePets || entity.id !in PetEntityTracker.petEntityIds) return
        val behindEntity = findEntityBehindPets(player) ?: run { cir.returnValue = ActionResult.PASS; return }
        MinecraftClient.getInstance().interactionManager?.interactEntity(player, behindEntity, hand)
        cir.returnValue = ActionResult.SUCCESS
    }

    private fun findEntityBehindPets(player: PlayerEntity): Entity? {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return null
        val reach = player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE)
        val eyePos = player.eyePos
        val lookVec = player.getRotationVec(1.0f)
        val endPos = eyePos.add(lookVec.multiply(reach))
        val searchBox = player.boundingBox.stretch(lookVec.multiply(reach)).expand(1.0, 1.0, 1.0)

        var closest: Entity? = null
        var closestDist = Double.MAX_VALUE

        for (entity in world.getOtherEntities(player, searchBox)) {
            if (entity.id in PetEntityTracker.petEntityIds) continue
            if (!entity.canHit()) continue
            val hit = entity.boundingBox.expand(0.3).raycast(eyePos, endPos)
            if (hit.isPresent) {
                val dist = eyePos.squaredDistanceTo(hit.get())
                if (dist < closestDist) {
                    closestDist = dist
                    closest = entity
                }
            }
        }

        return closest
    }
}
