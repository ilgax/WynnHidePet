package dev.ilgax.wynnhidepet.client

import dev.ilgax.wynnhidepet.getConfig
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft

/**
 * Hides pet chatter ("Myhsin: tsss") and pet level-ups.
 *
 * Wynncraft prefixes system chat with glyphs from its chat/prefix font, positioned by space-font characters:
 *
 *   first pet line of a run:  U+CFFFC  U+E016 (pet icon)  U+CFFFF  U+E002 (stripe)  U+CFFFE   Myhsin: tsss
 *   later lines of the run:   U+CFFFC  U+E001 (stripe only)  U+D0006                         Myhsin: whsshh
 *
 * Later lines carry no pet marker at all, so they are recognised by the previous line or the speaker's name.
 */
object PetChatFilter {

    private const val PET_ICON = 0xE016
    private const val CONTINUATION_ICON = 0xE001
    private const val CONTINUATION_MARK = 0xD0006

    private var lastWasPet = false

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (overlay || !Wynncraft.isConnected(Minecraft.getInstance())) return@register true

            val text = message.string
            val isPet = when (leadingIcon(text)) {
                PET_ICON -> true
                CONTINUATION_ICON -> lastWasPet || speaker(text) in PetEntityTracker.knownPetNames
                else -> false
            }
            lastWasPet = isPet
            if (!isPet) return@register true

            DebugCommand.recordPacket { "CHAT ${visibleText(text)}" }
            PetEffectFilter.onPetChat()

            val hide = getConfig().hidePetChat
            if (hide) DebugOverlay.push("§dHid pet chat §7${visibleText(text)}")
            !hide
        }
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

    /** "Myhsin" for "<prefix> Myhsin: tsss". */
    private fun speaker(text: String): String? = visibleText(text).substringBefore(": ", "").ifEmpty { null }

    private fun visibleText(text: String): String {
        val sb = StringBuilder(text.length)
        text.replace(Regex("§."), "").codePoints().forEach { if (it < 0xE000) sb.appendCodePoint(it) }
        return sb.toString().trim()
    }
}
