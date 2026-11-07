# Wynncraft Hide Pets

A client-side Fabric mod for Minecraft **1.21.11** that hides pets on Wynncraft, both yours and other players', so they don't block your view. You keep your pet perks without a pet in your face.

It only works while you're connected to Wynncraft.

## Features

- **Hide pets**: Models, nametags and hitboxes, at any distance and the moment they load. Press **`H`** or run **`/whp toggle`** to toggle (`/whp toggle on`/`off` to set it directly).
- **Click through pets**: Attacks and right-clicks will go through hidden pets to whatever is behind them.
- **Hide pet chat**: Hides pet chat messages. Works while pets are shown too.
- **Mute pet sounds**: Mutes the noises pets make. Works while pets are shown too.
- **Hides only pets**: Mobs, NPCs and combat dummies standing next to a pet remain visible.

## Settings

Open them through [Mod Menu](https://modrinth.com/mod/modmenu).

| Setting | Default | What it does |
|---|---|---|
| Hide Pets | off | Hide pets (same as pressing `H` or `/whp toggle`) |
| Show Toggle Message | on | Popup (toast) when pressing `H` or running `/whp toggle` |
| Hide Pet Chat | Follow Hide Pets | Hide pet chat messages: Off, On, or Follow Hide Pets (only while pets are hidden, so `H` switches it too) |
| Hide Pet Sounds | Follow Hide Pets | Mute sounds pets make: Off, On, or Follow Hide Pets |
| Enable Debug Tools | off | On-screen feed of what the mod hides or mutes, and the `/whp debug` command |
| Debug Feed | all on | Pick which hides (chat, sounds) show in the on-screen feed |

## Requirements

- Minecraft 1.21.11 with Fabric Loader
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.13.8+ (Kotlin 2.3)
- [Cloth Config API](https://modrinth.com/mod/cloth-config)
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, for the settings screen)

Any 1.21.11 version of Fabric API and Cloth Config works, Fabric Language Kotlin needs Kotlin 2.3 or newer.

## A pet isn't hidden, or something else is

Please open an [issue](https://github.com/ilgax/WynnHidePet/issues) with a debug dump:

1. Turn on **Enable Debug Tools** (Experimental tab).
2. Stand next to the pet and run `/whp debug`, or `/whp debug <seconds> <range>` to record for a while (e.g. `/whp debug 30 16`).
3. Click the file name in chat to open the dump, and attach it to the issue.

## How detection works

Every Wynncraft pet nametag carries a `[pet]` label. It is drawn with Wynncraft's resource pack font, as the exact characters `U+E060 U+E03F U+E034 U+E043 U+E062`. Mob nametags only carry an `[lv]` label. The mod starts from those nametags and collects the rest of each pet from the entity IDs Wynncraft gave it when it spawned. Three layouts are known:

- **Model before nametag**: a root item display with its model parts riding it, then the nametag, then the hitbox, all on consecutive IDs.
- **Model after nametag**: the nametag, then invisible armor stand / item display parts, then the hitbox (e.g. Heroes Guide).
- **Ridden mob**: a mob carrying the nametag as a passenger (e.g. Veteran Creeper).

Nothing outside those ID runs is collected, so how close another entity stands or when it loaded doesn't matter.

A scan runs at the end of the tick a text display or hitbox spawns and again on the next tick (nametag text can arrive a tick late), plus every 10 ticks. Once part of a pet, an entity stays hidden until it unloads or you change worlds, so pets don't flicker.

Pet chat lines start with Wynncraft's pet chat icon (`U+E016`); follow-up lines are matched by the previous line or the pet's name. Pet sounds are recognised by playing right on a pet, or by arriving together with a pet chat line.

## License

[GPL-3.0-or-later](LICENSE)
