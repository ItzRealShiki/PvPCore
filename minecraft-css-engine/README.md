# Minecraft CSS Engine

Standalone Paper plugin that renders Minecraft inventory GUIs from CSS-inspired `.mcss` files. It does not depend on PvPCore or CustomGuiTest.

## Textured GUI mode (ItemsAdder / Oraxen)

The engine uses a layered design:

1. Every button is a real Bukkit `ItemStack` of the selected vanilla material (for example `DIAMOND_SWORD`, `PLAYER_HEAD`, or `COMPARATOR`). The item remains in its inventory slot and the engine handles the slot click normally.
2. In textured mode, the engine assigns the item model `mcss:gui_hidden` and hides its tooltip. The supplied resource-pack model maps to `minecraft:empty`, so the icon is not rendered by the client. It is still a real item stack, not an empty slot.
3. A large font image/glyph is rendered from the inventory title over the vanilla inventory. The PNG contains the visible button artwork. Set the CSS `slot` to the inventory slot that lies underneath each drawn button.

This requires the client resource pack to contain the hidden-item model and the registered GUI glyph. ItemsAdder or Oraxen can assemble and distribute that pack automatically after the provided config is added to the relevant plugin folders and the pack is rebuilt.

### ItemsAdder

Copy `resourcepack/itemsadder/contents/mcss` into `plugins/ItemsAdder/contents/mcss`. Place your GUI image at:

`plugins/ItemsAdder/contents/mcss/resourcepack/assets/mcss/textures/gui/dashboard.png`

The provided `configs/gui.yml` registers the font image `mcss_dashboard`. The menu uses this title placeholder:

```
:offset_-16::mcss_dashboard:
```

Run `/iazip` after adding the image and config.

### Oraxen

Copy `resourcepack/oraxen/glyphs/mcss.yml` into `plugins/Oraxen/glyphs/mcss.yml`. Copy `resourcepack/oraxen/pack/assets/mcss/items/gui_hidden.json` into `plugins/Oraxen/pack/assets/mcss/items/gui_hidden.json`. Place your PNG at:

`plugins/Oraxen/pack/textures/gui/dashboard.png`

The provided glyph config registers `mcss_dashboard`. The menu uses:

```
<shift:-16><glyph:mcss_dashboard>
```

Run `/oraxen reload all` after adding the texture and configs.

Only one of ItemsAdder or Oraxen is needed. When both are installed, `texture-provider: auto` prefers Oraxen. Set it to `itemsadder` or `oraxen` to choose explicitly.

### Example textured menu

```css
screen {
    title: "NOVA Dashboard";
    rows: 6;
    texture-provider: auto;
    title-oraxen: "<shift:-16><glyph:mcss_dashboard>";
    title-itemsadder: ":offset_-16::mcss_dashboard:";
    background: AIR;
    hide-buttons: true;
}

.play {
    slot: 20;
    item: DIAMOND_SWORD;
    action: command:spawn;
}

.profile {
    slot: 22;
    item: PLAYER_HEAD;
    action: open:profile;
}

.close {
    slot: 49;
    item: BARRIER;
    action: close;
}
```

### What "hidden button" means

The button's material and stack remain server-side. The resource pack only changes the client-side visual model of that GUI copy of the item. No button artwork is created from the item itself: the GUI PNG is drawn over the inventory by the title glyph. Therefore the slot positions in the .mcss file must match the locations of the illustrated buttons in your PNG.

If no supported font-image plugin is active, the engine falls back to a normal title and leaves button models visible by default. If you explicitly set `render: hidden`, make sure the client pack contains `mcss:gui_hidden`.

## Plain mode

Without ItemsAdder or Oraxen, `/mcss` still works as a normal inventory GUI. Omit provider-specific titles or set `texture-provider: none`. Buttons remain visible unless you explicitly set `render: hidden`.

## Commands

- `/mcss` — open `main.mcss`
- `/mcss <name>` — open a menu by name
- `/mcss open <name>` — open a menu explicitly
- `/mcss list` — list available menus (admin)
- `/mcss reload` — ensure the menu directory/default file exists (admin); menu files are parsed whenever opened

## Supported properties

- `screen`: `title`, `rows` (1–6), `background`, `texture-provider` (`auto`, `oraxen`, `itemsadder`, `none`), `title-oraxen`, `title-itemsadder`, `hide-buttons`
- component selectors (`.className` or `#id`): `slot`, `item` (or `material`), `text`, `color`, `lore`, `amount`, `glow`, `render` (`hidden`/`normal`), `action`
- actions: `close`, `message:<text>`, `command:<player command>`, `open:<menu name>`
- placeholders: `%player%`, `%world%`, `%online%`, `%max_players%`, `%x%`, `%y%`, `%z%`

## Platform limitations

This is a CSS-inspired language for Paper inventory GUIs, not browser CSS. Inventory clicks remain slot-based. The custom image is rendered in the inventory title, not as a native window texture. Arbitrary pixel-precise clickable areas, HTML elements, and browser hover states are not supported.

## Build

Run `mvn --batch-mode clean package` inside this directory. The output is `target/MinecraftCSSEngine-1.0.0.jar`.
