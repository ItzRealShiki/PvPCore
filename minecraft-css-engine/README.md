# Minecraft CSS Engine

Standalone Paper plugin that creates inventory GUIs from CSS-inspired `.mcss` files. It does not depend on PvPCore or CustomGuiTest.

## Important: custom artwork needs a resource pack

A Paper plugin alone cannot replace the vanilla inventory artwork. The client must receive a resource pack containing the NOVA background glyph and the hidden-item model. This project includes a finished 176x222 dashboard image and setup files for ItemsAdder and Oraxen.

After the plugin starts once, its setup files are exported to `plugins/MinecraftCSSEngine/setup/`.

### ItemsAdder (recommended)
1. Copy `plugins/MinecraftCSSEngine/setup/itemsadder/contents/mcss` into `plugins/ItemsAdder/contents/mcss`.
2. Run `/iazip`.
3. Make sure the generated ItemsAdder pack is enabled and sent to your client.
4. Run `/mcss`.

The included `mcss.yml` registers `mcss_dashboard` as a font image. The PNG is already in `textures/gui/dashboard.png`; no extra image download is required.

### Oraxen
1. Copy `setup/oraxen/glyphs/mcss.yml` into `plugins/Oraxen/glyphs/mcss.yml`.
2. Copy `setup/oraxen/pack/assets/mcss/items/gui_hidden.json` into `plugins/Oraxen/pack/assets/mcss/items/gui_hidden.json`.
3. Copy `setup/oraxen/pack/textures/gui/dashboard.png` into `plugins/Oraxen/pack/textures/gui/dashboard.png`.
4. Run `/oraxen reload all`, ensure the generated pack is enabled and sent to your client, then run `/mcss`.

The provider is optional at runtime, but required to see the custom image. If neither provider is available, the engine falls back to a normal inventory and keeps the Minecraft items visible.

## How the buttons work

The artwork is a font image/glyph drawn over the inventory. Every button is a real Bukkit ItemStack stored in ordinary inventory slots. In textured mode, the engine assigns `mcss:gui_hidden` as its item model. The supplied resource-pack definition maps this to `minecraft:empty`, hiding the item icon while keeping the real item in the slot. Inventory clicks still trigger the button's configured action.

The `hitbox` property lets a button span adjacent slots:
- `hitbox: 1x1` — one slot (default)
- `hitbox: 3x2` — six adjacent slots share one action
- Width can be 1–9 and height 1–6, provided the area stays in the menu and does not overlap another button.

The sample dashboard uses Play at slot 27 (3x2), Profile at slot 30 (3x2), Settings at slot 33 (3x2), and Close at slot 52 (2x1).

For ItemsAdder, the engine uses the official `TexturedInventoryWrapper` API and checks that the font image is registered before opening the GUI. For Oraxen, it uses the glyph in the inventory title. Missing texture setup is logged and the engine falls back to a visible-item inventory rather than silently hiding every button.

## Example

```css
screen {
    title: "NOVA Dashboard";
    rows: 6;
    texture-provider: auto;
    texture-image: "mcss:mcss_dashboard";
    title-oraxen: "<glyph:mcss_dashboard>";
    background: AIR;
    hide-buttons: true;
}

.play {
    slot: 27;
    hitbox: 3x2;
    item: DIAMOND_SWORD;
    action: command:spawn;
}
```

## Commands

- `/mcss` — open `main.mcss`
- `/mcss <name>` — open a menu by name
- `/mcss open <name>` — open a menu explicitly
- `/mcss list` — list available menus (admin)
- `/mcss reload` — ensure the menu directory/default file exists (admin); menu files are parsed when opened

## Supported properties

- `screen`: `title`, `rows` (1–6), `background`, `texture-provider` (`auto`, `itemsadder`, `oraxen`, `none`), `texture-image`, `title-oraxen`, `title-offset`, `texture-offset`, `hide-buttons`
- Components: `slot`, `hitbox`, `item` (or `material`), `text`, `color`, `lore`, `amount`, `glow`, `render` (`hidden`/`normal`), `action`
- Actions: `close`, `message:<text>`, `command:<player command>`, `open:<menu name>`
- Placeholders: `%player%`, `%world%`, `%online%`, `%max_players%`, `%x%`, `%y%`, `%z%`

## Build

Run `mvn --batch-mode clean package` inside `minecraft-css-engine`. Output: `target/MinecraftCSSEngine-1.0.0.jar`.
