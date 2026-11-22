# PvPCore

Standalone Paper plugin for PvP training, separate flat practice worlds, and GUI administration.

## Requirements
- Java 25
- Gradle 9.8.1
- Paper 26.2

## Build
```bash
gradle clean build
```
Output: `build/libs/PvPCore-1.0.5.jar`

## Commands
- `/bot` — Opens the training NPC menu.
- `/rtp` — Opens the GUI to choose Plains, Desert, Snow, or Badlands.
- `/admin` — Opens the administration GUI.
- `/gm` or `/gamemode` — Opens the selector GUI; accepts a mode and optional player.
- `/gms [player]` — Sets Survival mode.
- `/gmc [player]` — Sets Creative mode.
- `/gma [player]` — Sets Adventure mode.
- `/gmsp [player]` — Sets Spectator mode.

All in-game messages, menu titles, item names, and lore use Unicode small-caps styling. User-facing plugin text is English. `/pvp` is not registered as an alias.

## Practice arenas

Use `/prac` to open the practice menu and choose one of five arena layouts:
- **Colosseum** — sandstone stadium towers and stepped spectator ledges.
- **Ancient Ruins** — broken stone pillars and mossy walls.
- **Overgrown** — trees and mossy cover around an open combat lane.
- **Nether Fortress** — basalt towers and polished blackstone details.
- **End Citadel** — purpur towers and End-rod accents.

Each player receives a separate practice instance, so multiple arenas can run at the same time. A 1v1 uses the challenger's selected arena. Every selection is a complete build preset: it determines the floor layers, wall palette, trim, corner structures, lighting and arena decorations. There is no separate ground-theme selector and no glass enclosure.

- **Colosseum** — smooth sandstone floor, cut/chiseled sandstone trim, sandstone stadium walls and spectator tiers.
- **Ancient Ruins** — stone-brick floor layers, cracked and mossy detailing, broken pillars and ruined walls.
- **Overgrown** — grass, dirt, rooted-dirt and moss floor layers, mossy walls, trees and natural cover.
- **Nether Fortress** — polished-blackstone floor, basalt/nether-brick layers, blackstone walls and Nether details.
- **End Citadel** — end-stone-brick floor, purpur layers and walls, End-themed towers and lighting.

A cyan particle forcefield appears only for players within the configured `practice.forcefield.activation-distance` (default 6 blocks) of the invisible inner boundary. Movement is stopped at the boundary without teleporting players back to the center.

## Training NPC
The `/bot` menu lets you configure and spawn a stationary training NPC. Left-click the Shield Reaction or Totem Refill setting to lower its difficulty; right-click to raise it. The selected level is shown in the item's lore:
- **Inf. Totem** — enabled by default; the NPC triggers a totem effect instead of dying and never consumes a totem.
- **Shield** — optional; remains raised and returns to its ready state after the selected reaction delay.
- NPCs do not move or chase players. Use the remove option in the menu or the admin menu to remove them.

## RTP worlds
Each world is created the first time it is selected and then saved by the server:
- `pvp_plains` — Flat grass world.
- `pvp_desert` — Flat sand world.
- `pvp_snow` — Flat snow world.
- `pvp_badlands` — Flat terracotta world.

The random teleport radius for each world is configurable in `config.yml` under `worlds.<key>.radius`. World names can also be changed there before first creation.

## Permissions
Only these three permission nodes are defined:
- `Member.Core` — Use bot training and RTP.
- `Admin.Core` — Administration and gamemode management.
- `Owner.Core` — Full access; bypasses lower-level permission checks.

Assign permissions through LuckPerms or another permission manager. No extra permission nodes are required.
