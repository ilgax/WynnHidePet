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
    var enableLenientClustering: Boolean = true
    var searchRadius: Double = 48.0
    var clusterDistanceLimit: Double = 1.2
    var useClusterDistanceLimit: Boolean = false
    var clusterAgeTolerance: Int = 5

    override fun validatePostLoad() {
        updateFrequency = updateFrequency.coerceAtLeast(1)
        memoryDurationTicks = memoryDurationTicks.coerceIn(1, 12000) // max 10 mins
        searchRadius = searchRadius.coerceIn(8.0, 128.0)
        clusterDistanceLimit = clusterDistanceLimit.coerceIn(0.1, 5.0)
        clusterAgeTolerance = clusterAgeTolerance.coerceIn(0, 60)
    }
}

fun getConfig(): ModConfig = AutoConfig.getConfigHolder(ModConfig::class.java).config
