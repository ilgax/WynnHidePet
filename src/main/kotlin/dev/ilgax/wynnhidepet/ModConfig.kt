package dev.ilgax.wynnhidepet

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

@Config(name = "wynnhidepet")
class ModConfig : ConfigData {
    var hidePets: Boolean = false

    companion object {
        fun get(): ModConfig = AutoConfig.getConfigHolder(ModConfig::class.java).config
    }
}
