# Minecraft CSS Engine

Standalone Paper plugin that renders Minecraft inventory GUIs from CSS-inspired `.mcss` files. It does not depend on PvPCore or CustomGuiTest and requires no client mod or mandatory resource pack.

## Install

1. Build the project with Java 25 and Maven.
2. Put `target/MinecraftCSSEngine-1.0.0.jar` into the server's `plugins` folder.
3. Start the Paper server once. The plugin creates `plugins/MinecraftCSSEngine/menus/main.mcss`.
4. Edit or add `.mcss` files in that menus folder.

## Commands

- `/mcss` — open `main.mcss`
- `/mcss <name>` — open a menu by name
- `/mcss open <name>` — open a menu explicitly
- `/mcss list` — list available menus (admin)
- `/mcss reload` — re-read the menu directory (admin)

## Example stylesheet

```css
screen {
    title: "NOVA Dashboard";
    rows: 3;
    background: BLACK_STAINED_GLASS_PANE;
}

.play {
    slot: 11;
    material: DIAMOND_SWORD;
    text: "Play";
    color: #55ddff;
    lore: "Join the server|Click to continue";
    action: command:spawn;
    glow: true;
}

.settings {
    slot: 13;
    material: COMPARATOR;
    text: "Settings";
    color: #a78bfa;
    lore: "Configure your experience";
    action: open:settings;
}

.close {
    slot: 15;
    material: BARRIER;
    text: "Close";
    action: close;
}
```

## Supported properties

- `screen`: `title`, `rows` (1–6), `background` (any Bukkit material; use `AIR` for an empty background)
- Components selected by `.className` or `#id`: `slot`, `material`, `text`, `color` (hex), `lore` (separate lines with `|`), `amount` (1–64), `glow` (true/false), `action`
- Actions: `close`, `message:<text>`, `command:<player command>`, `open:<menu name>`
- Placeholders: `%player%`, `%world%`, `%online%`, `%max_players%`, `%x%`, `%y%`, `%z%`
- Block comments: `/* comment */`

Every component needs a unique selector and a valid inventory `slot`. Component slots are zero-based (0–8 in the first row, 9–17 in the second row, etc.). Menu files are parsed when opened, so saving a file is enough for the next open; `/mcss reload` ensures the menu directory and default file exist.

## Platform limitation

This is a CSS-inspired language for Paper inventory GUIs, not browser CSS. A server inventory is built from slots and item stacks: arbitrary pixel placement, real mouse hover states, HTML, and browser-style animations are not available in vanilla inventory screens. This engine focuses on flexible menu screens, item buttons, navigation, text, colors, and click actions.

## Build

Run `mvn --batch-mode clean package` inside this directory. The output is `target/MinecraftCSSEngine-1.0.0.jar`.
