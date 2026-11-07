package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.Identifier

/** On-screen feed of what the mod just intercepted, shown while debug tools are enabled. */
object DebugOverlay {

    private const val SHOW_MS = 6000L
    private const val FADE_MS = 1000L
    private const val MAX_LINES = 8

    private class Entry(val text: String, val time: Long)

    private val entries = ArrayDeque<Entry>()

    fun register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("wynnhidepet", "debug_feed")) { graphics, _ ->
            render(graphics)
        }
    }

    /** What a feed line is about; each kind can be switched off in the settings. */
    enum class Kind { CHAT, SOUND }

    fun push(kind: Kind, text: String) {
        val config = getConfig()
        if (!config.enableDebugTools) return
        val shown = when (kind) {
            Kind.CHAT -> config.debugFeedChat
            Kind.SOUND -> config.debugFeedSounds
        }
        if (!shown) return
        entries.addLast(Entry(text, System.currentTimeMillis()))
        while (entries.size > MAX_LINES) entries.removeFirst()
    }

    private fun render(graphics: GuiGraphics) {
        val secondsLeft = DebugCommand.captureSecondsLeft
        if (entries.isEmpty() && secondsLeft == null) return
        val now = System.currentTimeMillis()
        entries.removeIf { now - it.time > SHOW_MS }

        val font = Minecraft.getInstance().font
        var y = 4
        if (secondsLeft != null) {
            val text = "§eDebug capture: §f${secondsLeft}s"
            graphics.fill(2, y - 2, 6 + font.width(text), y + 9, 0x80000000.toInt())
            graphics.drawString(font, text, 4, y, 0xFFFFFFFF.toInt(), true)
            y += 12
        }
        for (entry in entries) {
            val age = now - entry.time
            val alpha = if (age > SHOW_MS - FADE_MS) ((SHOW_MS - age) * 255 / FADE_MS).toInt() else 255
            if (alpha < 8) continue
            graphics.fill(2, y - 2, 6 + font.width(entry.text), y + 9, (alpha / 2) shl 24)
            graphics.drawString(font, entry.text, 4, y, (alpha shl 24) or 0xFFFFFF, true)
            y += 12
        }
    }
}
