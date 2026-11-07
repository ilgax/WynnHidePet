package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * Decides which sound and particle packets belong to pets, and feeds every packet to an active
 * /whp debug capture so new pet effects can be identified from real data.
 */
object PetEffectFilter {

    // Observed: pet sounds sit ~0.1 blocks from the pet's model and 2+ blocks from the player; the player's
    // own sounds and some pet chatter are centred on the player's block, ~0.7 blocks from the player, and a
    // spell sound was seen 0.9 blocks from a pet that stood right beside the player.
    private const val PLAYER_SOUND_RADIUS_SQ = 1.5 * 1.5
    private const val PET_SOUND_RADIUS_SQ = 0.5 * 0.5
    private const val PLAYER_MARGIN = 1.0

    // Sounds played on the player, held back until the end of the tick in case a pet chat line claims them.
    private val heldSounds = ArrayList<ClientboundSoundPacket>()

    fun shouldHideEntitySound(sound: Identifier, source: SoundSource, entityId: Int): Boolean {
        val isPet = entityId in PetEntityTracker.petEntityIds
        DebugCommand.recordPacket { "SOUND@ENTITY $sound source=$source entity=$entityId pet=$isPet" }
        if (!isPet || !getConfig().soundsHidden) return false
        DebugOverlay.push(DebugOverlay.Kind.SOUND, "§bMuted pet sound §7${sound.path}")
        return true
    }

    fun shouldHideSound(packet: ClientboundSoundPacket): Boolean {
        val sound = packet.sound.value().location()
        DebugCommand.recordPacket { "SOUND $sound source=${packet.source} ${describePosition(packet.x, packet.y, packet.z)}" }

        val client = Minecraft.getInstance()
        val level = client.level ?: return false
        val player = client.player ?: return false
        if (!getConfig().soundsHidden) return false
        // Every observed pet sound uses MASTER; music (RECORDS), blocks and other players use their own sources.
        if (packet.source != SoundSource.MASTER) return false

        // Pets' own sounds (ambient noises, chatter) play right on the pet. Drop them, unless the player is
        // close as well, since then it may just as well be the player's own sound (e.g. a spell).
        val playerDistSq = player.position().distanceToSqr(packet.x, packet.y, packet.z)
        val petDistSq = nearestPetDistSq(level, packet.x, packet.y, packet.z)
        if (petDistSq <= PET_SOUND_RADIUS_SQ && sqrt(playerDistSq) >= sqrt(petDistSq) + PLAYER_MARGIN) {
            DebugCommand.recordPacket { "DROPPED $sound" }
            DebugOverlay.push(DebugOverlay.Kind.SOUND, "§bMuted pet sound §7${sound.path}")
            return true
        }

        // Some pet chatter plays its sound on the player instead, one packet before the chat line, so it can't
        // be recognised yet. Hold it until the end of the tick: onPetChat() drops it, releaseHeldSounds() plays it.
        if (playerDistSq <= PLAYER_SOUND_RADIUS_SQ) {
            heldSounds.add(packet)
            return true
        }
        return false
    }

    /** Called for every pet chat line: drops the sound that announced it. */
    fun onPetChat() {
        if (!getConfig().soundsHidden) return
        for (packet in heldSounds) {
            val sound = packet.sound.value().location()
            DebugCommand.recordPacket { "DROPPED-WITH-CHAT $sound" }
            DebugOverlay.push(DebugOverlay.Kind.SOUND, "§bMuted pet chat sound §7${sound.path}")
        }
        heldSounds.clear()
    }

    /** Called at the end of every client tick: plays the held sounds no pet chat line claimed. */
    fun releaseHeldSounds(client: Minecraft) {
        if (heldSounds.isEmpty()) return
        val level = client.level
        if (level != null) {
            for (p in heldSounds) {
                level.playSeededSound(client.player, p.x, p.y, p.z, p.sound, p.source, p.volume, p.pitch, p.seed)
            }
        }
        heldSounds.clear()
    }

    private fun nearestPetDistSq(level: ClientLevel, x: Double, y: Double, z: Double): Double {
        var best = Double.MAX_VALUE
        for (id in PetEntityTracker.petEntityIds) {
            val d = level.getEntity(id)?.position()?.distanceToSqr(x, y, z) ?: continue
            if (d < best) best = d
        }
        return best
    }

    fun shouldHideParticles(particle: ParticleOptions, x: Double, y: Double, z: Double, count: Int): Boolean {
        DebugCommand.recordPacket {
            "PARTICLE ${BuiltInRegistries.PARTICLE_TYPE.getKey(particle.type)} count=$count ${describePosition(x, y, z)}"
        }
        return false
    }

    private fun describePosition(x: Double, y: Double, z: Double): String {
        val client = Minecraft.getInstance()
        val level = client.level ?: return "pos=?"
        val pos = Vec3(x, y, z)
        var nearestPet: Int? = null
        var nearestPetDist = Double.MAX_VALUE
        for (id in PetEntityTracker.petEntityIds) {
            val d = level.getEntity(id)?.position()?.distanceTo(pos) ?: continue
            if (d < nearestPetDist) {
                nearestPetDist = d
                nearestPet = id
            }
        }
        val playerDist = client.player?.position()?.distanceTo(pos)
        return "pos=%.2f,%.2f,%.2f self=%.2f pet=%s petDist=%.2f".format(
            x, y, z, playerDist ?: -1.0, nearestPet ?: "-", if (nearestPet == null) -1.0 else nearestPetDist
        )
    }
}
