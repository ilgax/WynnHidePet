package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.ModConfig
import me.shedaniel.autoconfig.AutoConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

class WynnhidepetClient : ClientModInitializer {

    companion object {
        private val category = KeyBinding.Category(Identifier.of("wynnhidepet", "keys"))
        val toggleKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.wynnhidepet.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
            )
        )
    }

    override fun onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val isOnWynncraft = client.currentServerEntry?.address?.contains("wynncraft", ignoreCase = true) == true

            if (isOnWynncraft) {
                PetEntityTracker.update(client)
            }

            while (toggleKey.wasPressed()) {
                val holder = AutoConfig.getConfigHolder(ModConfig::class.java)

                if (!isOnWynncraft) {
                    if (holder.config.showToggleMessage) {
                        client.player?.sendMessage(
                            Text.literal("§cWynnCraft Hide Pets only works on Wynncraft!"),
                            false
                        )
                    }
                    continue
                }

                holder.config.hidePets = !holder.config.hidePets
                holder.save()

                if (holder.config.showToggleMessage) {
                    client.player?.sendMessage(
                        Text.literal("§ePets: ${if (holder.config.hidePets) "§cHIDDEN" else "§aVISIBLE"}"),
                        false
                    )
                }
            }
        }
    }
}
