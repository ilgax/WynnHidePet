package dev.ilgax.wynnhidepet

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

@Config(name = "wynnhidepet")
class ModConfig : ConfigData {
    var hidePets: Boolean = false
    var showToggleMessage: Boolean = true
    // Independent of hidePets, so chat and sounds can be muted while pets stay visible.
    var hidePetChat: Boolean = false
    var hidePetSounds: Boolean = false
    var enableDebugTools: Boolean = false
}

fun getConfig(): ModConfig = AutoConfig.getConfigHolder(ModConfig::class.java).config
