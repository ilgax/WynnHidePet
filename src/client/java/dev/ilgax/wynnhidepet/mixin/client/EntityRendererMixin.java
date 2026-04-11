package dev.ilgax.wynnhidepet.mixin.client;

import dev.ilgax.wynnhidepet.ModConfigKt;
import dev.ilgax.wynnhidepet.client.PetEntityTracker;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void onShouldRender(
        T entity,
        Frustum frustum,
        double x, double y, double z,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ModConfigKt.getConfig().getHidePets() && PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId())) {
            cir.setReturnValue(false);
        }
    }
}
