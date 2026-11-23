# Resource-pack integration assets

The server sets `minecraft:item_model=mcss:gui_hidden` on hidden click-target stacks. This item model definition maps to `minecraft:empty`, so the client renders no item icon while the actual Bukkit item stack stays in its slot and continues to trigger the configured action.

## ItemsAdder

Copy `itemsadder/contents/mcss` into `plugins/ItemsAdder/contents/mcss`. Place your custom background image at:

`plugins/ItemsAdder/contents/mcss/resourcepack/assets/mcss/textures/gui/dashboard.png`

The matching font image config is in `configs/gui.yml`. Copy the hidden model JSON to the same namespace's `resourcepack/assets/mcss/items/gui_hidden.json` (already included). Run `/iazip` to build the pack.

The configured font image key is `mcss_dashboard`; use `:offset_-16::mcss_dashboard:` as the title string in your .mcss menu.

## Oraxen

Copy `oraxen/glyphs/mcss.yml` to `plugins/Oraxen/glyphs/mcss.yml`, copy `oraxen/pack/assets/mcss/items/gui_hidden.json` to `plugins/Oraxen/pack/assets/mcss/items/gui_hidden.json`, and put your PNG at `plugins/Oraxen/pack/textures/gui/dashboard.png`. Run `/oraxen reload all`.

The configured glyph key is `mcss_dashboard`; use `<shift:-16><glyph:mcss_dashboard>` as the title string in your .mcss menu.

Only one of ItemsAdder or Oraxen is needed. Both handle resource-pack building and delivery; players receive the pack through the server's configured pack flow. Make sure players accept/download the pack, otherwise the invisible-model asset and glyphs will be missing.

## Vanilla pack compatibility

The supplied item model uses the 1.21.4+ `minecraft:item_model` system and `minecraft:empty` item-model type. It targets the Minecraft 26.2 resource-pack format (88). The project is built against Paper 26.2.
