# WynnCraft Hide Pets

A powerful, robust Fabric mod for Minecraft **1.21.11** designed specifically for Wynncraft. 

Identifies and hides pets in Wynncraft to reduce screen clutter and improve performance in high-traffic areas, while ensuring that important quest NPCs and interactions remain visible.

## Features

- **Robust Pet Detection**: Uses exact-age clustering and Unicode nametag matching to hide all components of a pet (hitboxes, nametags, and visual models).
- **ID Memory**: Remembers pets even after their nametags fade out or are removed by the server, ensuring they don't "flicker" back into existence.
- **Lenient Clustering**: fallback detection that can identify complex pets even before their nametag appears.
- **Configurable Settings**: 
  - **Memory TTL**: Adjust how long pets stay hidden after vanishing.
  - **Search Radius**: Control how far the mod scans for pets.
  - **Lenient Toggle**: Enable/Disable the heuristic-based fallback, disable and please report if non-pet entities are being hidden.
  - **Cluster Age Tolerance**: Adjust the max age difference between pet parts to handle server spawn lag, increase if your ping is high.
- **Smart Toggle**: Press **`H`** to instantly toggle pet visibility. Performs a deep reset to ensure pets appear/disappear immediately.

## Requirements

- **Minecraft**: 1.21.11
- **Fabric Loader**: 0.18.4+
- **Fabric API**: Latest for 1.21.11
- **Fabric Language Kotlin**: Latest for 1.21.11
- **Cloth Config API**: Required for settings.
- **ModMenu**: (Optional) For in-game configuration.

## Experimental Features

Includes advanced settings for power users, located in the **Experimental** tab of the configuration screen. These settings are disabled by default as the mod's standard heuristic is already highly accurate.

- **Use Distance Limit**: When enabled, the mod will only cluster entities that are within a very small horizontal distance (configurable) of the pet's hitbox.
  - **Pros**: Provides a "hard stop" to prevent NPCs from being hidden even in pixel-perfect proximity to a pet.
  - **Cons**: Can cause parts of your pet (like models or floating nametags) to flicker or remain visible if the server teleports the hitbox faster than the model can follow due to network lag.
- **Cluster Distance Limit**: Controls the specific radius (in blocks) for the distance check above. A value of **1.2** is recommended if you choose to enable this feature, max value of **5.0** is allowed.

> **Should I use these?** Most players should keep these **Disabled**. Only enable them if you notice specific NPCs in crowded areas are being hidden by nearby player pets.