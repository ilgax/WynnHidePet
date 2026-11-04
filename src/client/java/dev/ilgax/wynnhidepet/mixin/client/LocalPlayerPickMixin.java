package dev.ilgax.wynnhidepet.mixin.client;

import dev.ilgax.wynnhidepet.ModConfigKt;
import dev.ilgax.wynnhidepet.client.PetEntityTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Makes the crosshair look straight through hidden pets, so attacks and right-clicks land on whatever
 * is behind them instead of on an invisible hitbox.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerPickMixin {

    @ModifyArg(
        method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"),
        index = 4
    )
    private static Predicate<Entity> wynnhidepet_skipPetsInPick(Predicate<Entity> original) {
        return wynnhidepet_skipPets(original);
    }

    @ModifyArg(
        method = "raycastHitResult",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/AttackRange;getClosesetHit(Lnet/minecraft/world/entity/Entity;FLjava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"),
        index = 2
    )
    private Predicate<Entity> wynnhidepet_skipPetsInAttackRange(Predicate<Entity> original) {
        return wynnhidepet_skipPets(original);
    }

    @Unique
    private static Predicate<Entity> wynnhidepet_skipPets(Predicate<Entity> original) {
        return entity -> original.test(entity)
            && !(ModConfigKt.getConfig().getHidePets() && PetEntityTracker.INSTANCE.getPetEntityIds().contains(entity.getId()));
    }
}
