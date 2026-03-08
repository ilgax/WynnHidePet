package dev.ilgax.wynnhidepet.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.ilgax.wynnhidepet.ModConfig
import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.client.gui.screen.Screen

class ModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<Screen> {
        return ConfigScreenFactory { parent ->
            AutoConfig.getConfigScreen(ModConfig::class.java, parent).get()
        }
    }
}
