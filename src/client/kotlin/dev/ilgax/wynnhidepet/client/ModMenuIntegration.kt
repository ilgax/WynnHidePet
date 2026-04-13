package dev.ilgax.wynnhidepet.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.ilgax.wynnhidepet.ModConfig
import dev.ilgax.wynnhidepet.getConfig
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<Screen> {
        return ConfigScreenFactory { parent ->
            val builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Wynn Hide Pet Settings"))

            val config = getConfig()
            val entryBuilder = builder.entryBuilder()
            val category = builder.getOrCreateCategory(Component.literal("General"))
            val experimentalCategory = builder.getOrCreateCategory(Component.literal("Experimental"))

            category.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Hide Pets"), config.hidePets)
                    .setDefaultValue(true)
                    .setSaveConsumer { config.hidePets = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Show Toggle Message"), config.showToggleMessage)
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("Enable or disable chat message when pressing the toggle key"))
                    .setSaveConsumer { config.showToggleMessage = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startIntField(Component.literal("Update Frequency"), config.updateFrequency)
                    .setDefaultValue(5)
                    .setMin(1)
                    .setMax(100)
                    .setTooltip(Component.literal("How often to scan for pets in ticks"))
                    .setSaveConsumer { config.updateFrequency = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startIntField(Component.literal("Memory Duration (Ticks)"), config.memoryDurationTicks)
                    .setDefaultValue(600)
                    .setMin(20)
                    .setMax(12000)
                    .setTooltip(Component.literal("How long pets stay hidden after vanishing"))
                    .setSaveConsumer { config.memoryDurationTicks = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startDoubleField(Component.literal("Search Radius"), config.searchRadius)
                    .setDefaultValue(48.0)
                    .setMin(8.0)
                    .setMax(128.0)
                    .setTooltip(Component.literal("Radius to look for pets"))
                    .setSaveConsumer { config.searchRadius = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Enable Lenient Clustering"), config.enableLenientClustering)
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("Allow hiding pets without visible nametags if their model matches"))
                    .setSaveConsumer { config.enableLenientClustering = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startIntSlider(Component.literal("Cluster Age Tolerance"), config.clusterAgeTolerance, 0, 60)
                    .setDefaultValue(5)
                    .setTooltip(Component.literal("Max age difference in ticks between hitbox and parts. Increase if pets still show up during lag."))
                    .setSaveConsumer { config.clusterAgeTolerance = it }
                    .build()
            )

            // --- Experimental Category ---

            experimentalCategory.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Use Distance Limit"), config.useClusterDistanceLimit)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Restrict pet components to a specific horizontal radius. Experimental: May cause missed pets if they lag far behind their hitboxes."))
                    .setSaveConsumer { config.useClusterDistanceLimit = it }
                    .build()
            )

            experimentalCategory.addEntry(
                entryBuilder.startDoubleField(Component.literal("Cluster Distance Limit"), config.clusterDistanceLimit)
                    .setDefaultValue(1.2)
                    .setMin(0.1)
                    .setMax(5.0)
                    .setTooltip(Component.literal("Max horizontal distance between hitbox and pet components."))
                    .setSaveConsumer { config.clusterDistanceLimit = it }
                    .build()
            )

            builder.setSavingRunnable {
                AutoConfig.getConfigHolder(ModConfig::class.java).save()
            }

            builder.build()
        }
    }
}
