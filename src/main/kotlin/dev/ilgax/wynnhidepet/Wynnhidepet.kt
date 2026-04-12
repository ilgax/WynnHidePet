package dev.ilgax.wynnhidepet

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.api.ModInitializer

class Wynnhidepet : ModInitializer {

    override fun onInitialize() {
        AutoConfig.register(ModConfig::class.java, ::GsonConfigSerializer)
    }
}
