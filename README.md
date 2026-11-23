# PvPCore

Standalone Paper plugin for PvP training, private practice rooms, and GUI administration.

## Requirements
- Java 25
- Gradle 9.8.1
- Paper 26.2

## Build
```bash
gradle clean build
```
Output: `build/libs/PvPCore-1.0.14.jar`

## Commands
- `/prac` — Opens the private PvP practice hub and training modes.
- `/kits` — Opens personal Practice and Duel kit editors.
- `/spawn` — Leaves Practice, a Duel, or Parkour and returns to Spawn.
- `/leave` — Leaves an active practice session or duel.
- `/duel` and `/duels` — Challenges and manages private 1v1 duels.
- `/lag` — Opens the integrated Prac performance dashboard.
- `/lag report` — Prints a diagnostics snapshot.
- `/lag profile` — Starts a 10-minute Spark profile; check the server console for its report URL.
- `/admin` — Opens the administration GUI.
- `/bot` — Opens the training NPC menu.
- `/options` — Opens player visual options.
- `/scoreboard` — Configures the practice scoreboard.
- `/gm` or `/gamemode` — Opens the game-mode selector.
- `/setspawn` — Sets the configured spawn position.

## Practice arenas

Use `/prac` to choose between two original test layouts. Each player has a separate practice instance; duels use the challenger's selected arena.

- **Anubis Rise** — Egyptian desert-temple arena with sand and sandstone, red-sandstone details, four monumental gates and obelisks.
- **Tiny End Coliseum** — End-themed coliseum with end-stone, purpur rings, four gatehouses, corner towers, End-rod beacons and spectator tiers.

The arenas are built inside the shared void Practice world `prac_void`; player rooms are separated by distance. The Spawn hub is a separate void world named `Spawn`.

## World cleanup

On first startup with this build, Prac waits until both required worlds (`Spawn` and `prac_void`) are loaded successfully. It then moves players out of every other loaded world, unloads those worlds, and deletes obsolete world folders asynchronously. It retains only the `Spawn` and `prac_void` world folders.

If a world cannot be unloaded safely, Prac does not delete its files. It retries the cleanup and logs any remaining issue. Back up your server files before installing this build: the cleanup is intentionally destructive for all other world folders, as requested.

## Performance diagnostics

The integrated `/lag` dashboard shows TPS/MSPT, recent tick spikes, JVM memory and garbage collection, loaded worlds/chunks/entities, and measured Prac operation times. Use `/lag profile` while the lag spike is happening to start Spark's deeper profiler. The Spark report URL is printed in the server console.

## Training NPC

The `/bot` menu lets you configure and spawn a training bot, including its shield reaction and totem behavior. Bot availability is restricted to the supported Paper 26.2 implementation.

## Permissions
- `Member.Core` — Use practice rooms and member features.
- `Admin.Core` — Administration and diagnostics.
- `Owner.Core` — Full access and permission bypass.

Assign permissions through LuckPerms or another permission manager.
