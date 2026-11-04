package dev.ilgax.wynnhidepet

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

@Config(name = "wynnhidepet")
class ModConfig : ConfigData {
    var hidePets: Boolean = false
    var showToggleMessage: Boolean = true
    var updateFrequency: Int = 5
    var memoryDurationTicks: Int = 600
    // Also hide pets whose nametag isn't loaded, as long as their hitbox directly follows their model.
    var enableLenientClustering: Boolean = true
    var searchRadius: Double = 48.0
    // Independent of hidePets, so chat and sounds can be muted while pets stay visible.
    var hidePetChat: Boolean = false
    var hidePetSounds: Boolean = false
    var enableDebugTools: Boolean = false

    override fun validatePostLoad() {
        updateFrequency = updateFrequency.coerceAtLeast(1)
        memoryDurationTicks = memoryDurationTicks.coerceIn(1, 12000) // max 10 mins
        searchRadius = searchRadius.coerceIn(8.0, 128.0)
    }
}

fun getConfig(): ModConfig = AutoConfig.getConfigHolder(ModConfig::class.java).config
