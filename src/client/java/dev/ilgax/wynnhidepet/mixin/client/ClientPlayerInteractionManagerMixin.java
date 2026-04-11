package dev.ilgax.wynnhidepet.mixin.client;

import dev.ilgax.wynnhidepet.ModConfigKt;
import dev.ilgax.wynnhidepet.client.PetEntityTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(PlayerEntity player, Entity entity, CallbackInfo ci) {
        if (ModConfigKt.getConfig().getHidePets() && PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId())) {
            ci.cancel();
        }
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void onInteractEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!ModConfigKt.getConfig().getHidePets() || !PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId())) return;
        Entity behind = findEntityBehindPets(player);
        if (behind == null) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }
        ClientPlayerInteractionManager im = MinecraftClient.getInstance().interactionManager;
        if (im == null) { cir.setReturnValue(ActionResult.PASS); return; }
        im.interactEntity(player, behind, hand);
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void onInteractEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hitResult, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!ModConfigKt.getConfig().getHidePets() || !PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId())) return;
        Entity behind = findEntityBehindPets(player);
        if (behind == null) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }
        ClientPlayerInteractionManager im = MinecraftClient.getInstance().interactionManager;
        if (im == null) { cir.setReturnValue(ActionResult.PASS); return; }
        im.interactEntity(player, behind, hand);
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    @Unique
    @Nullable
    private Entity findEntityBehindPets(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;

        // Use the modern interaction range helper (1.20.5+)
        double reach = player.getEntityInteractionRange();
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookVec.multiply(reach));

        // Use a tighter bounding box for the initial search to improve performance.
        // We only care about entities along the look vector.
        Box searchBox = player.getBoundingBox().stretch(lookVec.multiply(reach)).expand(0.5);

        Entity closest = null;
        double closestDistSq = reach * reach;

        // Filter out pets early to avoid redundant raycasts.
        for (Entity e : client.world.getOtherEntities(player, searchBox,
                ent -> !PetEntityTracker.INSTANCE.getPetEntityIds().contains(ent.getId()) && ent.canHit())) {

            Optional<Vec3d> hit = e.getBoundingBox().expand(e.getTargetingMargin()).raycast(eyePos, endPos);
            if (hit.isPresent()) {
                double distSq = eyePos.squaredDistanceTo(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = e;
                }
            }
        }

        return closest;
    }
}
