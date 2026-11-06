package dev.ilgax.wynnhidepet.client

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

/**
 * `/whp toggle [on|off]`: the toggle key as a command. Kept under our own root on purpose: claiming `/toggle`
 * would make Fabric swallow Wynncraft's own `/toggle <option>` commands instead of sending them to the server.
 */
object ToggleCommand {

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("whp").then(
                    literal("toggle")
                        .executes { ctx -> WynnhidepetClient.setHidePets(ctx.source.client, null); 1 }
                        .then(literal("on").executes { ctx -> WynnhidepetClient.setHidePets(ctx.source.client, true); 1 })
                        .then(literal("off").executes { ctx -> WynnhidepetClient.setHidePets(ctx.source.client, false); 1 })
                )
            )
        }
    }
}
