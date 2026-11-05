package dev.ilgax.wynnhidepet.mixin.client;

import dev.ilgax.wynnhidepet.client.PetEffectFilter;
import dev.ilgax.wynnhidepet.client.WynnhidepetClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Drops sound and particle packets that belong to hidden pets. Each hook runs after the handler has
 * hopped onto the render thread, so it sees the same entity state as the renderer.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(
        method = "handleSoundEvent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void wynnhidepet_onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (wynnhidepet_safely(() -> PetEffectFilter.INSTANCE.shouldHideSound(packet))) {
            ci.cancel();
        }
    }

    @Inject(
        method = "handleSoundEntityEvent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void wynnhidepet_onEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (wynnhidepet_safely(() -> PetEffectFilter.INSTANCE.shouldHideEntitySound(packet.getSound().value().location(), packet.getSource(), packet.getId()))) {
            ci.cancel();
        }
    }

    @Inject(
        method = "handleParticleEvent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void wynnhidepet_onParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (wynnhidepet_safely(() -> PetEffectFilter.INSTANCE.shouldHideParticles(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ(), packet.getCount()))) {
            ci.cancel();
        }
    }

    /** An exception in a packet handler disconnects the player, so a failing filter lets the packet through. */
    @Unique
    private static boolean wynnhidepet_safely(BooleanSupplier filter) {
        try {
            return filter.getAsBoolean();
        } catch (RuntimeException e) {
            WynnhidepetClient.Companion.getLOGGER().error("Pet sound/particle filter failed; letting the packet through", e);
            return false;
        }
    }
}
