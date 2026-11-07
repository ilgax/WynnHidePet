package dev.ilgax.wynnhidepet

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

@Config(name = "wynnhidepet")
class ModConfig : ConfigData {
    var hidePets: Boolean = false
    var showToggleMessage: Boolean = true
    // Each set on its own: always hidden, never, or only while hidePets is on (so the toggle key switches it too).
    var petChat: HideMode = HideMode.FOLLOW_HIDE_PETS
    var petSounds: HideMode = HideMode.FOLLOW_HIDE_PETS
    var enableDebugTools: Boolean = false
    var debugFeedChat: Boolean = true
    var debugFeedSounds: Boolean = true

    // Up to 1.2.0 these were plain on/off switches. Read once and folded into petChat/petSounds; Gson leaves
    // null fields out, so they disappear from the file on the next save.
    private var hidePetChat: Boolean? = null
    private var hidePetSounds: Boolean? = null

    override fun validatePostLoad() {
        hidePetChat?.let { petChat = if (it) HideMode.ON else HideMode.OFF }
        hidePetSounds?.let { petSounds = if (it) HideMode.ON else HideMode.OFF }
        hidePetChat = null
        hidePetSounds = null
    }

    /** Whether pet chat is hidden right now. */
    val chatHidden: Boolean get() = petChat.isActive(hidePets)

    /** Whether pet sounds are muted right now. */
    val soundsHidden: Boolean get() = petSounds.isActive(hidePets)
}

enum class HideMode(val label: String) {
    OFF("Off"),
    ON("On"),
    FOLLOW_HIDE_PETS("Follow Hide Pets");

    fun isActive(hidePets: Boolean): Boolean = this == ON || (this == FOLLOW_HIDE_PETS && hidePets)
}

fun getConfig(): ModConfig = AutoConfig.getConfigHolder(ModConfig::class.java).config
