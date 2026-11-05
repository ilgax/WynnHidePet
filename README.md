# Wynncraft Hide Pets

A client-side Fabric mod for Minecraft **1.21.11** that hides pets on Wynncraft, both yours and other players', so they don't block your view. You keep your pet perks without a pet in your face.

It only does anything while you're connected to Wynncraft.

## Features

- **Hide pets**: models, nametags and hitboxes, at any distance and the moment they load. Press **`H`** to toggle.
- **Click through pets**: attacks and right-clicks go to whatever is behind a hidden pet.
- **Hide pet chat**: hides pet chatter (`Myhsin: tsss`) and pet level-ups. Works while pets are shown too.
- **Mute pet sounds**: mutes the noises pets make. Works while pets are shown too.
- **Hides only pets**: mobs, NPCs and combat dummies standing next to a pet stay visible.

## Settings

Open them through [Mod Menu](https://modrinth.com/mod/modmenu).

| Setting | Default | What it does |
|---|---|---|
| Hide Pets | off | Hide pets (same as pressing `H`) |
| Show Toggle Message | on | Popup (toast) when pressing `H` |
| Hide Pet Chat | off | Hide pet chat lines and level-ups |
| Hide Pet Sounds | off | Mute sounds pets make |
| Enable Debug Tools *(Experimental)* | off | On-screen feed of what the mod hides or mutes, and the `/whp debug` command |

## Requirements

- Minecraft 1.21.11 with Fabric Loader
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.13.8+ (Kotlin 2.3)
- [Cloth Config API](https://modrinth.com/mod/cloth-config)
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, for the settings screen)

Any 1.21.11 version of Fabric API and Cloth Config works; Fabric Language Kotlin needs Kotlin 2.3 or newer.

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

## Development

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

### Releasing

Bump `mod_version` in `gradle.properties` (e.g. `1.1.5+1.21.11`), commit, then push a matching tag without the Minecraft version:

```bash
git tag v1.1.5
git push origin v1.1.5
```

[`release.yml`](.github/workflows/release.yml) builds the jar and publishes a GitHub release. It also uploads the version to Modrinth when the repository has a `MODRINTH_TOKEN` secret (scope: *Create versions*) and a `MODRINTH_PROJECT` variable. Versions below 1.0 or with a `-` suffix are published as pre-releases (Modrinth beta/alpha).

To keep a release off Modrinth, put `[skip modrinth]` in an annotated tag's message:

```bash
git tag -a v1.1.5 -m "[skip modrinth]"
git push origin v1.1.5
```

## License

[GPL-3.0-or-later](LICENSE.txt)
