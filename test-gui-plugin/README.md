# CustomGuiTest

Standalone Paper 26.2 / Java 25 multi-screen GUI test plugin.

## Seven screens

- `/test nova` — Main hub with navigation cards.
- `/test dashboard` — Status cards and quick actions.
- `/test profile` — Player identity, world, ping, level and food.
- `/test settings` — Aurora/Violet theme switch and sound preview.
- `/test showcase` — Gallery of all custom buttons, borders and panels.
- `/test stats` — Live health, food, level, ping, world and coordinates.
- `/test lab` — Design/technical information and limitations.

Aliases and navigation:
- `/test` or `/test open` opens the NOVA hub.
- `/test gui <name>` and `/test ui <name>` open a selected screen.
- Direct forms such as `/test profile` are supported.
- `/test theme`, `/test close`, and `/test help` are available.
- Tab completion suggests all screen names, including after `gui` or `ui`.

## Visual implementation

The plugin draws custom pixel art to individual 128x128 Filled Map textures using Bukkit/Paper MapRenderer. Every screen has a bitmap title made from custom letter tiles. The Aurora and Violet palettes update the screen frame and panel artwork. All top-inventory clicks and drags are cancelled so GUI items cannot be removed.

No resource pack is required for this build. The Minecraft inventory frame and native client UI remain vanilla; a later resource-pack-based iteration can replace those client-side elements.

Map IDs are persisted in config.yml and reused across restarts.

## Build

Run `mvn --batch-mode clean package` from this directory.
Output: `target/CustomGuiTest-1.0.0.jar`.
