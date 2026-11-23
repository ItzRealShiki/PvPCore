# Minecraft CSS Engine

Standalone Paper plugin that renders Minecraft inventory GUIs from CSS-inspired `.mcss` files. It does not depend on PvPCore or CustomGuiTest.

## Textured GUI mode (ItemsAdder / Oraxen)

The engine supports a layered GUI design:

1. The inventory contains real Bukkit item stacks (for example `DIAMOND_SWORD`, `PLAYER_HEAD`, or `COMPARATOR`). These are the actual clickable buttons and the engine runs their configured actions.
2. In textured mode, each button item receives the `mcss:gui_hidden` item model. A resource pack maps that model to `minecraft:empty`, so the item icon and tooltip are not drawn.
3. A large font image/glyph is rendered from the inventory title, over the vanilla inventory. The artwork contains the visible buttons and panels; align the corresponding `slot` values to the artwork's button centers.

This requires a client resource pack containing the hidden item model and the GUI font image. ItemsAdder or Oraxen can generate and distribute the pack automatically after you add the provided configuration to the plugin's content folders and build/reload their pack.

### ItemsAdder

Copy `resourcepack/itemsadder/contents/mcss` into `plugins/ItemsAdder/contents/mcss`, put your GUI PNG at `resourcepack/assets/mcss/textures/gui/dashboard.png` (or the matching ItemsAdder content texture path), then run `/iazip`.

The example uses this font image placeholder in the menu title:

```
:offset_-16::mcss_dashboard:
```

### Oraxen

Copy `resourcepack/oraxen/pack/assets/mcss` into `plugins/Oraxen/pack/assets/mcss` and copy `resourcepack/oraxen/glyphs/mcss.yml` into `plugins/Oraxen/glyphs/mcss.yml`. Put your GUI PNG at `plugins/Oraxen/pack/textures/gui/dashboard.png`, then run `/oraxen reload all`.

The example uses this title:

```
<shift:-16><glyph:mcss_dashboard>
```

You only need one of ItemsAdder or Oraxen. When both are installed, `texture-provider: auto` prefers Oraxen. Set it to `itemsadder` or `oraxen` to choose explicitly.

## Example textured menu

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
    render: hidden;
    action: command:spawn;
}

.profile {
    slot: 22;
    item: PLAYER_HEAD;
    render: hidden;
    action: open:profile;
}

.close {
    slot: 49;
    item: BARRIER;
    render: hidden;
    action: close;
}
```

The `item` property selects the real Minecraft material for the click target. Use `render: hidden` to suppress its model using the resource pack. In textured mode, `hide-buttons: true` does this by default; `render: normal` makes an individual item visible again. Slot numbers are zero-based.

### Resource-pack assets

The project includes `resourcepack/assets/mcss/items/gui_hidden.json`. It maps `mcss:gui_hidden` to the vanilla empty item model. The same file is included in the ItemsAdder and Oraxen pack integration folders.

The GUI background is not a separate inventory texture property in Minecraft. It is a font image/glyph drawn from the title and shifted over the inventory. This is the established method used by textured inventory menus; the exact title shift and glyph vertical alignment depend on your PNG and should be adjusted in the provider configuration.

## Plain mode

Without ItemsAdder or Oraxen, `/mcss` still works as a normal inventory GUI. Use `texture-provider: none` or omit the provider-specific title properties. Buttons remain visible unless you explicitly set `render: hidden`. Hiding items without the resource pack will make them invisible only on clients that have the model asset.

## Commands

- `/mcss` — open `main.mcss`
- `/mcss <name>` — open a menu by name
- `/mcss open <name>` — open a menu explicitly
- `/mcss list` — list available menus (admin)
- `/mcss reload` — ensure the menu directory/default file exists (admin); files are parsed on open

## Other properties

- `screen`: `title`, `rows` (1–6), `background`, `texture-provider` (`auto`, `oraxen`, `itemsadder`, `none`), `title-oraxen`, `title-itemsadder`, `hide-buttons`
- components selected by `.className` or `#id`: `slot`, `item` (or `material`), `text`, `color`, `lore`, `amount`, `glow`, `render` (`hidden`/`normal`), `action`
- actions: `close`, `message:<text>`, `command:<player command>`, `open:<menu name>`
- placeholders: `%player%`, `%world%`, `%online%`, `%max_players%`, `%x%`, `%y%`, `%z%`

## Platform limitations

This is a CSS-inspired language for Paper inventory GUIs, not browser CSS. Inventory clicks remain slot-based. The custom image is rendered in the inventory title, not as a native window texture. Arbitrary pixel-precise clickable areas, HTML elements, and browser hover states are not supported.

## Build

Run `mvn --batch-mode clean package` inside this directory. The output is `target/MinecraftCSSEngine-1.0.0.jar`.
