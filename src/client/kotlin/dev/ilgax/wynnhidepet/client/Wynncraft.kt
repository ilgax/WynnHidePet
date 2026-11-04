package dev.ilgax.wynnhidepet.client

import net.minecraft.client.Minecraft

object Wynncraft {

    fun isConnected(client: Minecraft): Boolean {
        // The address may carry a port ("play.wynncraft.com:25565") or a trailing dot.
        val host = client.currentServer?.ip?.lowercase()?.substringBefore(':')?.trimEnd('.') ?: return false
        return host == "wynncraft.com" || host.endsWith(".wynncraft.com")
    }
}
