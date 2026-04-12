package dev.ilgax.wynnhidepet.mixin.client;

import dev.ilgax.wynnhidepet.ModConfigKt;
import dev.ilgax.wynnhidepet.client.PetEntityTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {

    @Unique
    private long wynnhidepet_lastInteractTick = -1L;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(Player player, Entity entity, CallbackInfo ci) {
        if (ModConfigKt.getConfig().getHidePets() && PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId())) {
            ci.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteractEntity(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!ModConfigKt.getConfig().getHidePets() || !PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId())) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) { cir.setReturnValue(InteractionResult.PASS); return; }
        long currentTick = client.level.getGameTime();
        if (wynnhidepet_lastInteractTick == currentTick) { cir.setReturnValue(InteractionResult.PASS); return; }
        
        Entity behind = findEntityBehindPets(player);
        if (behind != null) {
            MultiPlayerGameMode im = client.gameMode;
            if (im != null) {
                wynnhidepet_lastInteractTick = currentTick;
                InteractionResult result = im.interact(player, behind, hand);
                cir.setReturnValue(result);
                return;
            }
        }
        
        // If no entity behind or no gameMode, consume the click so it doesn't reach the pet
        cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void onInteractEntityAtLocation(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!ModConfigKt.getConfig().getHidePets() || !PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId())) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) { cir.setReturnValue(InteractionResult.PASS); return; }
        long currentTick = client.level.getGameTime();
        if (wynnhidepet_lastInteractTick == currentTick) { cir.setReturnValue(InteractionResult.PASS); return; }

        Entity behind = findEntityBehindPets(player);
        if (behind != null) {
            MultiPlayerGameMode im = client.gameMode;
            if (im != null) {
                wynnhidepet_lastInteractTick = currentTick;
                InteractionResult result = im.interact(player, behind, hand);
                cir.setReturnValue(result);
                return;
            }
        }

        // Always block/pass on the pet itself
        cir.setReturnValue(InteractionResult.PASS);
    }

    @Unique
    @Nullable
    private Entity findEntityBehindPets(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;

        // Mojang 1.21.1: getEntityInteractionRange() -> entityInteractionRange()
        double reach = player.entityInteractionRange();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.multiply(reach, reach, reach));

        // Use a tighter bounding box for the initial search
        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(reach)).inflate(0.5);

        Entity closest = null;
        double closestDistSq = reach * reach;

        // Filter out pets early to avoid redundant raycasts.
        for (Entity e : client.level.getEntities(player, searchBox,
                ent -> !PetEntityTracker.INSTANCE.getPetEntityIds().contains(ent.getId()) && ent.isPickable())) {

            Optional<Vec3> hit = e.getBoundingBox().inflate(e.getPickRadius()).clip(eyePos, endPos);
            if (hit.isPresent()) {
                double distSq = eyePos.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = e;
                }
            }
        }

        return closest;
    }
}
