# Minecraft CSS Engine

Standalone Paper plugin that renders inventory GUIs from CSS-inspired `.mcss` files. It does not depend on PvPCore or CustomGuiTest.

## Textured GUI mode (ItemsAdder / Oraxen)

The engine uses a layered design:

1. Each button is a real Bukkit `ItemStack` of the selected vanilla material (for example `DIAMOND_SWORD`, `PLAYER_HEAD`, or `COMPARATOR`). The item remains in its inventory slot and the engine handles that slot's click action.
2. In textured mode, the engine assigns the item model `mcss:gui_hidden` and hides its tooltip. The supplied resource-pack model maps to `minecraft:empty`, so the client draws no item icon even though the real item stack remains in the slot.
3. A large font image/glyph is rendered from the inventory title over the vanilla inventory. The PNG contains the visible button artwork. Set each CSS `slot` to the slot beneath the matching button in your PNG.

### Setup files included in the JAR

After installing the JAR and starting the server once, the plugin exports integration files to:

`plugins/MinecraftCSSEngine/setup/`

Choose ONE provider.

**ItemsAdder**
1. Copy `plugins/MinecraftCSSEngine/setup/itemsadder/contents/mcss` into `plugins/ItemsAdder/contents/mcss`.
2. Put your background image at `plugins/ItemsAdder/contents/mcss/resourcepack/assets/mcss/textures/gui/dashboard.png`.
3. Run `/iazip`.

The included config registers `mcss_dashboard`. The menu title uses `:offset_-16::mcss_dashboard:`.

**Oraxen**
1. Copy `plugins/MinecraftCSSEngine/setup/oraxen/glyphs/mcss.yml` into `plugins/Oraxen/glyphs/mcss.yml`.
2. Copy `plugins/MinecraftCSSEngine/setup/oraxen/pack/assets/mcss/items/gui_hidden.json` into `plugins/Oraxen/pack/assets/mcss/items/gui_hidden.json`.
3. Put your background image at `plugins/Oraxen/pack/textures/gui/dashboard.png`.
4. Run `/oraxen reload all`.

The included glyph config registers `mcss_dashboard`. The menu title uses `<shift:-16><glyph:mcss_dashboard>`.

Only one provider is required. When both are installed, `texture-provider: auto` prefers Oraxen. Choose `itemsadder` or `oraxen` explicitly to select one.

The PNG is your actual GUI artwork. It should contain the visible button designs at positions that align with your chosen inventory slots. The plugin intentionally does not invent a default background image.

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

`item` chooses the actual Minecraft item stack used as a click target. In textured mode, `hide-buttons: true` sets the invisible model by default. Use `render: normal` for an individual visible item or `render: hidden` to hide one explicitly. If no supported provider is active, the engine falls back to a normal title and leaves the button items visible by default.

## Commands

- `/mcss` — open `main.mcss`
- `/mcss <name>` — open a menu by name
- `/mcss open <name>` — open a menu explicitly
- `/mcss list` — list available menus (admin)
- `/mcss reload` — ensure the menu directory/default file exists (admin); menu files are parsed on open

## Supported properties

- `screen`: `title`, `rows` (1–6), `background`, `texture-provider` (`auto`, `oraxen`, `itemsadder`, `none`), `title-oraxen`, `title-itemsadder`, `hide-buttons`
- Components (`.className` or `#id`): `slot`, `item` (or `material`), `text`, `color`, `lore`, `amount`, `glow`, `render` (`hidden`/`normal`), `action`
- Actions: `close`, `message:<text>`, `command:<player command>`, `open:<menu name>`
- Placeholders: `%player%`, `%world%`, `%online%`, `%max_players%`, `%x%`, `%y%`, `%z%`

## Platform limitations

This is a CSS-inspired language for Paper inventory GUIs, not browser CSS. Inventory clicks remain slot-based. The custom image is rendered from the inventory title as a font glyph, not as a native Minecraft window texture. Arbitrary pixel-based click hitboxes, HTML elements, and browser hover states are not supported.

## Build

Run `mvn --batch-mode clean package` inside this directory. Output: `target/MinecraftCSSEngine-1.0.0.jar`.
