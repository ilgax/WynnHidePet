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
                    .setTooltip(Component.literal("How often to scan for pets in ticks (default: 5)"))
                    .setSaveConsumer { config.updateFrequency = it }
                    .build()
            )

            builder.setSavingRunnable {
                AutoConfig.getConfigHolder(ModConfig::class.java).save()
            }

            builder.build()
        }
    }
}
