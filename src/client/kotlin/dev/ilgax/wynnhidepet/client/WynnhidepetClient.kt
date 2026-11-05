package dev.ilgax.wynnhidepet.client

import com.mojang.blaze3d.platform.InputConstants
import dev.ilgax.wynnhidepet.ModConfig
import me.shedaniel.autoconfig.AutoConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Interaction
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WynnhidepetClient : ClientModInitializer {

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("wynnhidepet")

        private val CUSTOM_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("wynnhidepet", "keys")
        )

        val toggleKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.wynnhidepet.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CUSTOM_CATEGORY
            )
        )

        // One shared token, so pressing the key again updates the visible toast instead of stacking new ones.
        private val TOGGLE_TOAST = SystemToast.SystemToastId(2500L)
        private val TOAST_TITLE: Component = Component.literal("WynnCraft Hide Pets")

        private fun showToast(client: Minecraft, message: Component) {
            SystemToast.addOrUpdate(client.toastManager, TOGGLE_TOAST, TOAST_TITLE, message)
        }
    }

    private var ticks = 0
    private var lastLevel: ClientLevel? = null

    override fun onInitializeClient() {
        DebugCommand.register()
        PetChatFilter.register()
        DebugOverlay.register()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Wynncraft moves players between worlds (class select, world hops, housing) without
            // disconnecting; IDs remembered from the old world must not hide entities in the new one.
            if (client.level !== lastLevel) {
                lastLevel = client.level
                PetEntityTracker.reset()
            }

            val isOnWynncraft = Wynncraft.isConnected(client)

            if (isOnWynncraft) {
                ticks = (ticks + 1) % PetEntityTracker.SCAN_INTERVAL_TICKS
                // A pet nametag/hitbox spawned this tick or last tick (world change, teleport, pet summon):
                // scan now so the pet never renders before the next scheduled update.
                val spawnScan = PetEntityTracker.consumeSpawnScan()
                if (ticks == 0 || spawnScan) {
                    PetEntityTracker.update(client)
                }
            }

            DebugCommand.tick(client)
            PetEffectFilter.releaseHeldSounds(client)

            while (toggleKey.consumeClick()) {
                val holder = AutoConfig.getConfigHolder(ModConfig::class.java)
                val currentConfig = holder.config

                if (!isOnWynncraft) {
                    if (currentConfig.showToggleMessage) {
                        showToast(client, Component.literal("Only works on Wynncraft").withStyle(ChatFormatting.RED))
                    }
                    continue
                }

                currentConfig.hidePets = !currentConfig.hidePets
                holder.save()

                // Rescan right away so pets hide instantly. Rendering and clicking check hidePets themselves, and
                // the IDs stay tracked while showing pets if sound or chat hiding still needs them.
                PetEntityTracker.update(client)

                if (currentConfig.showToggleMessage) {
                    showToast(
                        client,
                        if (currentConfig.hidePets) Component.literal("Pets hidden").withStyle(ChatFormatting.RED)
                        else Component.literal("Pets visible").withStyle(ChatFormatting.GREEN)
                    )
                }
            }
        }

        // Text content arrives in a data packet after the spawn, so only flag here and scan at tick end.
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (entity is Display.TextDisplay || entity is Interaction) PetEntityTracker.requestScan()
        }
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            PetEntityTracker.onEntityUnload(entity.id)
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            PetEntityTracker.reset()
        }
    }
}
