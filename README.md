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
- `/pvp` — GUI for the PvP training modes.
- `/rtp` — GUI to choose a separate flat world: Plains, Desert, Snow, or Badlands.
- `/admin` — GUI for bot administration, gamemodes and config reload.
- `/gm` or `/gamemode` — GUI selector; accepts mode and optional player arguments.

## RTP worlds
Each world is created the first time it is selected and then saved by the server:
- `pvp_plains` — flat grass world.
- `pvp_desert` — flat sand world.
- `pvp_snow` — flat snow world.
- `pvp_badlands` — flat terracotta world.

The random teleport radius for each world is configurable in `config.yml` under `worlds.<key>.radius`. World names can also be changed there before first creation.

## Permissions
Only these three permission nodes are defined:
- `Member.Core` — use PvP training and RTP.
- `Admin.Core` — admin dashboard and gamemode management.
- `Owner.Core` — full access (also bypasses the lower-level checks).

Assign them through LuckPerms or your permission manager. No extra permission nodes are required.

## Notes
Training opponents in this foundation version are ordinary server-side mobs. Advanced training behavior is not yet implemented.
