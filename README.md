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
Output: `build/libs/PvPCore-1.0.6.jar`

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

Use `/prac` to choose between two original test layouts inspired by the broad visual themes of the linked references. The paid schematic files are not bundled or downloaded.

- **Anubis Rise** — an Egyptian desert-temple arena with sand and sandstone floor layers, red-sandstone details, gold sun-disc spawn emblem, four monumental gates, obelisks and a stepped temple facade.
- **Tiny End Coliseum** — a compact End-themed coliseum with end-stone floor layers, purpur rings, four gatehouses, corner towers, End-rod beacons and two spectator tiers.

Each player has a separate practice instance; duels use the challenger's selected test arena. The layout scales to the existing Practice and Duel footprints so it does not create oversized schematic-sized worlds. Both presets control the whole build: floor, wall palette, trim, structures, lighting and decorations. The solid 12-block perimeter walls remain in place, with a cyan proximity forcefield at the inner boundary. Set `practice.forcefield.activation-distance` in `config.yml` to adjust how early it appears.

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
