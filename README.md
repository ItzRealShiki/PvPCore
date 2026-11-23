# PvPCore

Standalone Paper plugin for PvP training, separate flat practice worlds, and GUI administration.

## Requirements
- Java 25
- Maven 3.9+
- Paper 26.2

## Build
```bash
mvn clean package
```
Output: `target/PvPCore-1.0.0.jar`

## Commands
- `/bot` (alias: `/pvp`) — Opens the training NPC menu.
- `/rtp` — Opens the GUI to choose Plains, Desert, Snow, or Badlands.
- `/admin` — Opens the administration GUI.
- `/lag` — Opens the Prac lag diagnostics dashboard (Admin.Core).
- `/lag report` — Prints a text diagnostics report.
- `/lag profile` — Starts Paper's Spark profiler for 10 minutes; check the server console for the report URL.
- `/gm` or `/gamemode` — Opens the selector GUI; accepts a mode and optional player.
- `/gms [player]` — Sets Survival mode.
- `/gmc [player]` — Sets Creative mode.
- `/gma [player]` — Sets Adventure mode.
- `/gmsp [player]` — Sets Spectator mode.

All in-game messages, menu titles, item names, and lore use the same Unicode small-caps styling as LifestealCore. User-facing plugin text is English.

## Training NPC
The `/bot` menu lets you configure and spawn a stationary training NPC:
- **Inf. Totem** — enabled by default; the NPC triggers a totem effect instead of dying and never consumes a totem.
- **Shield** — optional; blocks frontal player attacks and becomes ready again after 0.1 seconds.
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


## Credits

Original work by **ItzRealShiki**. If you use PvPCore on a Minecraft server, follow the server-credit requirement in the repository's LICENSE.

## License

See [LICENSE](LICENSE). Third-party components remain subject to their own license terms.
