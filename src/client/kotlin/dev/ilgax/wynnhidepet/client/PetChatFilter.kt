package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Optional

/**
 * Hides pet chatter ("Myhsin: tsss") and pet level-ups.
 *
 * Wynncraft prefixes system chat with glyphs from its chat/prefix font, positioned by space-font characters:
 *
 *   first pet line of a run:  U+CFFFC  U+E016 (pet icon)  U+CFFFF  U+E002 (stripe)  U+CFFFE   Myhsin: tsss
 *   later lines of the run:   U+CFFFC  U+E001 (stripe only)  U+D0006                         Myhsin: whsshh
 *
 * Later lines share their glyphs with guild, party and private messages, so they are told apart by colour,
 * as Wynntils does: a gold prefix and name, then the text in pet beige (#FFDD99).
 */
object PetChatFilter {

    private const val PET_ICON = 0xE016
    private const val CONTINUATION_ICON = 0xE001
    private const val CONTINUATION_MARK = 0xD0006

    private const val PREFIX_COLOUR = 0xFFAA00 // ChatFormatting.GOLD
    private const val TEXT_COLOUR = 0xFFDD99

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            // An exception here would disconnect the player, so any failure lets the message through.
            runCatching { allow(message, overlay) }
                .onFailure { WynnhidepetClient.LOGGER.error("Pet chat filter failed; letting the message through", it) }
                .getOrDefault(true)
        }
    }

    private fun allow(message: Component, overlay: Boolean): Boolean {
        if (overlay || !Wynncraft.isConnected(Minecraft.getInstance())) return true

        val text = message.string
        val isPet = when (leadingIcon(text)) {
            PET_ICON -> true
            CONTINUATION_ICON -> hasPetColours(message)
            else -> false
        }
        if (!isPet) return true

        DebugCommand.recordPacket { "CHAT ${visibleText(text)}" }
        PetEffectFilter.onPetChat()

        val hide = getConfig().chatHidden
        if (hide) DebugOverlay.push(DebugOverlay.Kind.CHAT, "§dHid pet chat §7${visibleText(text)}")
        return !hide
    }

    /** First private-use glyph of the message, or null; a continuation line reports [CONTINUATION_ICON]. */
    private fun leadingIcon(text: String): Int? {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp > 0xFFFF) continue // spacer
            if (cp !in 0xE000..0xF8FF) return null
            if (cp == CONTINUATION_ICON) {
                return if (i < text.length && text.codePointAt(i) == CONTINUATION_MARK) CONTINUATION_ICON else cp
            }
            return cp
        }
        return null
    }

    /** Gold prefix (the first styled segment) and some visible text in pet beige. */
    private fun hasPetColours(message: Component): Boolean {
        var prefixColour: Int? = null
        var seenPrefix = false
        var hasPetText = false
        message.visit<Unit>({ style, segment ->
            if (segment.isNotEmpty()) {
                val colour = style.color?.value
                if (!seenPrefix) {
                    seenPrefix = true
                    prefixColour = colour
                }
                if (colour == TEXT_COLOUR && segment.isNotBlank()) hasPetText = true
            }
            Optional.empty()
        }, Style.EMPTY)
        return prefixColour == PREFIX_COLOUR && hasPetText
    }

    private fun visibleText(text: String): String {
        val sb = StringBuilder(text.length)
        text.replace(Regex("§."), "").codePoints().forEach { if (it < 0xE000) sb.appendCodePoint(it) }
        return sb.toString().trim()
    }
}
