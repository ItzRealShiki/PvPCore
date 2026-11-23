Minecraft CSS Engine - NOVA dashboard resource-pack setup

The plugin exports this folder to plugins/MinecraftCSSEngine/setup/ on first startup.

IMPORTANT:
A Paper plugin cannot replace the client's vanilla inventory artwork by itself. The custom GUI image and hidden-item model must be in a client resource pack. Install ONE provider below and make sure the generated pack is sent to players.

ITEMSADDER (recommended)
1. Copy "setup/itemsadder/contents/mcss" into "plugins/ItemsAdder/contents/mcss".
2. The included mcss.yml registers the font image and the dashboard PNG.
3. Run "/iazip".
4. Confirm the ItemsAdder resource pack is enabled and delivered to your client.
5. Run "/mcss".

ORAXEN
1. Copy "setup/oraxen/glyphs/mcss.yml" into "plugins/Oraxen/glyphs/mcss.yml".
2. Copy "setup/oraxen/pack/assets/mcss/items/gui_hidden.json" into "plugins/Oraxen/pack/assets/mcss/items/gui_hidden.json".
3. Copy "setup/oraxen/pack/textures/gui/dashboard.png" into "plugins/Oraxen/pack/textures/gui/dashboard.png".
4. Run "/oraxen reload all".
5. Confirm the Oraxen pack is enabled and delivered to your client, then run "/mcss".

The 176x222 image is aligned to a six-row chest inventory. Its buttons map to:
- Play: slot 27, 3x2 click area
- Profile: slot 30, 3x2 click area
- Settings: slot 33, 3x2 click area
- Close: slot 52, 2x1 click area

Each button is a real vanilla Bukkit ItemStack. The resource pack maps the special item model "mcss:gui_hidden" to "minecraft:empty", hiding its icon while the item and its click action stay in the inventory.

If the pack is missing or not accepted by the client, the background glyph and invisible item model will not render. In that case, use the fallback visible-items GUI until the pack is installed correctly.
