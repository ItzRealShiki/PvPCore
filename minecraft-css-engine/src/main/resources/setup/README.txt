Minecraft CSS Engine - ready-to-use GUI texture setup

The JAR contains a finished NOVA dashboard texture and exports all integration files to:
plugins/MinecraftCSSEngine/setup/

IMPORTANT: A Paper plugin alone cannot replace the client's vanilla inventory artwork. The custom image must be part of a client resource pack. Install ONE pack provider, copy its setup files, rebuild its pack, and make sure players receive it.

ITEMSADDER
1. Copy the folder "setup/itemsadder/contents/mcss" into "plugins/ItemsAdder/contents/mcss".
2. Run "/iazip" and make sure the generated pack is sent to players.
3. Restart the server or reload the menu, then open "/mcss".

ORAXEN
1. Copy "setup/oraxen/glyphs/mcss.yml" into "plugins/Oraxen/glyphs/mcss.yml".
2. Copy "setup/oraxen/pack/assets/mcss/items/gui_hidden.json" into "plugins/Oraxen/pack/assets/mcss/items/gui_hidden.json".
3. Copy "setup/oraxen/pack/textures/gui/dashboard.png" into "plugins/Oraxen/pack/textures/gui/dashboard.png".
4. Run "/oraxen reload all" and make sure the generated pack is sent to players.
5. Open "/mcss".

The overlay is 176x166 pixels. Buttons are real vanilla ItemStacks:
- Play: slot 19 with a 2x2 clickable area
- Profile: slot 22 with a 2x2 clickable area
- Settings: slot 25 with a 2x2 clickable area
- Close: slot 49 with a 2x1 clickable area

The item model "mcss:gui_hidden" maps to "minecraft:empty". The items stay in their server-side inventory slots and their click actions still work.

Players must have the generated resource pack loaded. If the pack is missing or rejected, the GUI will not look custom and hidden item models may be displayed as their normal vanilla items.
