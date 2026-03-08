package dev.ilgax.wynnhidepet.mixin.client

import dev.ilgax.wynnhidepet.client.PetEntityTracker
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.entity.Entity
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
        if (entity.id in PetEntityTracker.petEntityIds) {
            cir.returnValue = false
        }
    }
}
