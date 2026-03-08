package dev.ilgax.wynnhidepet

import com.mojang.brigadier.context.CommandContext
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class Wynnhidepet : ModInitializer {

    override fun onInitialize() {
        AutoConfig.register(ModConfig::class.java, ::GsonConfigSerializer)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("whp")
                    .then(literal("toggle")
                        .executes { ctx -> togglePets(ctx) }
                    )
                    .executes { ctx -> showHelp(ctx) }
            )
        }
    }

    private fun togglePets(ctx: CommandContext<ServerCommandSource>): Int {
        val holder = AutoConfig.getConfigHolder(ModConfig::class.java)
        holder.config.hidePets = !holder.config.hidePets
        holder.save()
        ctx.source.sendFeedback(
            { Text.literal("Pet visibility: ${if (holder.config.hidePets) "HIDDEN" else "VISIBLE"}") },
            false
        )
        return 1
    }

    private fun showHelp(ctx: CommandContext<ServerCommandSource>): Int {
        ctx.source.sendFeedback({ Text.literal("Usage: /whp toggle") }, false)
        return 1
    }
}
