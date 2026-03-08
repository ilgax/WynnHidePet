package dev.ilgax.wynnhidepet.mixin.client

import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity
import net.minecraft.entity.decoration.InteractionEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(EntityRenderer::class)
class EntityRendererMixin<T : Entity> {

    @Inject(method = ["shouldRender"], at = [At("HEAD")], cancellable = true)
    fun onShouldRender(
        entity: T,
        frustum: net.minecraft.client.render.Frustum,
        x: Double, y: Double, z: Double,
        cir: CallbackInfoReturnable<Boolean>
    ) {
        if (!getConfig().hidePets) return
        val player = MinecraftClient.getInstance().player ?: return

        // Wynncraft pets are item_display + interaction entities near the player
        if (entity is ItemDisplayEntity || entity is InteractionEntity) {
            if (entity.squaredDistanceTo(player) <= 36.0) { // within 6 blocks
                cir.returnValue = false
            }
        }
    }
}
