package dev.ilgax.wynnhidepet.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.ilgax.wynnhidepet.HideMode
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
                    .setDefaultValue(false)
                    .setSaveConsumer { config.hidePets = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startBooleanToggle(Component.literal("Show Toggle Message"), config.showToggleMessage)
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("Show a popup when pressing the toggle key"))
                    .setSaveConsumer { config.showToggleMessage = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startEnumSelector(Component.literal("Hide Pet Chat"), HideMode::class.java, config.petChat)
                    .setDefaultValue(HideMode.FOLLOW_HIDE_PETS)
                    .setEnumNameProvider { Component.literal((it as HideMode).label) }
                    .setTooltip(
                        Component.literal("Hide pet chat messages and level-ups."),
                        Component.literal("Follow Hide Pets: only while Hide Pets is on, so the toggle key switches it too.")
                    )
                    .setSaveConsumer { config.petChat = it }
                    .build()
            )

            category.addEntry(
                entryBuilder.startEnumSelector(Component.literal("Hide Pet Sounds"), HideMode::class.java, config.petSounds)
                    .setDefaultValue(HideMode.FOLLOW_HIDE_PETS)
                    .setEnumNameProvider { Component.literal((it as HideMode).label) }
                    .setTooltip(
                        Component.literal("Mute sounds pets make."),
                        Component.literal("Follow Hide Pets: only while Hide Pets is on, so the toggle key switches it too.")
                    )
                    .setSaveConsumer { config.petSounds = it }
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

            val feed = entryBuilder.startSubCategory(Component.literal("Debug Feed"))
            feed.add(
                entryBuilder.startBooleanToggle(Component.literal("Show Hidden Chat"), config.debugFeedChat)
                    .setDefaultValue(true)
                    .setSaveConsumer { config.debugFeedChat = it }
                    .build()
            )
            feed.add(
                entryBuilder.startBooleanToggle(Component.literal("Show Muted Sounds"), config.debugFeedSounds)
                    .setDefaultValue(true)
                    .setSaveConsumer { config.debugFeedSounds = it }
                    .build()
            )
            experimentalCategory.addEntry(feed.setTooltip(Component.literal("Which kinds of hides appear in the on-screen feed")).build())

            builder.setSavingRunnable {
                AutoConfig.getConfigHolder(ModConfig::class.java).save()
            }

            builder.build()
        }
    }
}
