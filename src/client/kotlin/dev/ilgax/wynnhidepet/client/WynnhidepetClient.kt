package dev.ilgax.wynnhidepet.client

import com.mojang.blaze3d.platform.InputConstants
import dev.ilgax.wynnhidepet.BuildConstants
import dev.ilgax.wynnhidepet.ModConfig
import dev.ilgax.wynnhidepet.getConfig
import me.shedaniel.autoconfig.AutoConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

class WynnhidepetClient : ClientModInitializer {

    companion object {
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
    }

    private var ticks = 0

    override fun onInitializeClient() {
        if (BuildConstants.DEBUG) {
            DebugCommand.register()
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val serverIp = client.currentServer?.ip?.lowercase() ?: ""
            val isOnWynncraft = serverIp.endsWith("wynncraft.com")
            val config = getConfig()

            if (isOnWynncraft) {
                ticks = (ticks + 1) % config.updateFrequency.coerceAtLeast(1)
                if (ticks == 0) {
                    PetEntityTracker.update(client)
                }
            }

            if (BuildConstants.DEBUG) {
                DebugCommand.tick(client)
            }

            while (toggleKey.consumeClick()) {
                val holder = AutoConfig.getConfigHolder(ModConfig::class.java)
                val currentConfig = holder.config

                if (!isOnWynncraft) {
                    if (currentConfig.showToggleMessage) {
                        client.player?.displayClientMessage(
                            Component.literal("§cWynnCraft Hide Pets only works on Wynncraft!"),
                            false
                        )
                    }
                    continue
                }

                currentConfig.hidePets = !currentConfig.hidePets
                holder.save()

                if (currentConfig.hidePets) {
                    // Trigger an immediate update to hide pets instantly
                    PetEntityTracker.update(client)
                } else {
                    // Force clear everything if showing pets
                    PetEntityTracker.reset()
                }

                if (currentConfig.showToggleMessage) {
                    client.player?.displayClientMessage(
                        Component.literal("§ePets: ${if (currentConfig.hidePets) "§cHIDDEN" else "§aVISIBLE"}"),
                        false
                    )
                }
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            PetEntityTracker.reset()
        }
    }
}
