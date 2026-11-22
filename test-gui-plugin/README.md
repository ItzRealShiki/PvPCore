# CustomGuiTest

A standalone Paper 26.2 / Java 25 plugin that demonstrates a heavily customized inventory GUI without asking players to install a resource pack.

## Visual implementation

- The native inventory title is intentionally blank.
- The first row forms a custom graphical NOVA title from four individually drawn bitmap glyph tiles.
- Every background, border and button is a Filled Map item whose 128x128 image is drawn by a server-side MapRenderer.
- Buttons use original pixel artwork: profile, design lab, color theme, interaction, status, back and close.
- The map IDs are persisted in config.yml and reused on later restarts.
- The GUI has a matching second page with a graphical LAB heading.
- All clicks and drags in the top inventory are protected.

The maps use the normal vanilla Filled Map item as a carrier. The art is delivered as map pixels by the server, not as a resource pack. The actual chest window frame and inventory panel remain vanilla; the custom graphic is drawn inside the inventory slots. This is the strongest fully server-side visual approach without changing the client's font or item textures.

## Commands

- `/test` or `/test open` — Open the main custom GUI.
- `/test info` — Open the design-lab page.
- `/test close` — Close the current inventory.
- `/test theme` — Switch between the cyan and violet visual themes.
- `/test help` — Show command help.

The first argument has tab autocomplete. Alias: `/testgui`.

## Build

Run `mvn --batch-mode clean package` from this directory.
Output: `target/CustomGuiTest-1.0.0.jar`.
