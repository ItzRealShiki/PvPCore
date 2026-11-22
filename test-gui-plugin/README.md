# CustomGuiTest

A standalone Paper plugin used to test a custom inventory layout and a custom inventory title without requiring players to install a resource pack.

## Requirements

- Paper 26.2
- Java 25

## Commands

- `/test` or `/test open` — Open the main custom GUI.
- `/test info` — Open the information page.
- `/test close` — Close the currently open inventory.
- `/test theme` — Switch the cyan/violet menu theme.
- `/test help` — Show command help.

The first argument has tab autocomplete. The alias `/testgui` is also available.

## GUI features

- 6-row, 54-slot chest inventory.
- Adventure Component inventory title with multiple colors and formatting.
- Custom border, background, button placement, item names and lore.
- Clickable demo buttons and a second information page with its own title.
- Inventory clicks and drags are cancelled to prevent players from taking decorative menu items.
- No resource pack or client-side download is required.

## Visual limitation

This customizes the inventory contents and title. The actual chest window texture and the vanilla item textures remain unchanged. Changing those textures for all players would still require a resource pack.

## Build

From this directory, run:

```bash
mvn --batch-mode clean package
```

Output: `target/CustomGuiTest-1.0.0.jar`
