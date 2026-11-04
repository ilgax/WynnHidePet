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
                    .setTooltip(Component.literal("Also hide pets whose nametag hasn't loaded yet, if their hitbox directly follows their model"))
                    .setSaveConsumer { config.enableLenientClustering = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Hide Pet Chat"), config.hidePetChat)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Hide pet chat messages and level-ups, even while pets are shown"))
                    .setSaveConsumer { config.hidePetChat = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Hide Pet Sounds"), config.hidePetSounds)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Mute sounds pets make, even while pets are shown"))
                    .setSaveConsumer { config.hidePetSounds = it }
                    .build()
            )

            // --- Experimental Category ---

            experimentalCategory.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Enable Debug Tools"), config.enableDebugTools)
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Shows what the mod intercepts in an on-screen feed, and enables the /whp debug command."))
                    .setSaveConsumer { config.enableDebugTools = it }
                    .build()
            )

            builder.setSavingRunnable {
                AutoConfig.getConfigHolder(ModConfig::class.java).save()
            }

            builder.build()
        }
    }
}
