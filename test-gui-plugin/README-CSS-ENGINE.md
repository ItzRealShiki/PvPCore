# Minecraft CSS Engine (Paper)

A CSS-inspired stylesheet renderer for Paper inventory menus. It does not use a client mod or resource pack.

## Commands
- `/cssui` - open the default `main.mcss` menu
- `/cssui <name>` - open a named menu
- `/cssui open <name>` - explicit open syntax
- `/cssui reload` - reload menu files

Menu files live in `plugins/CustomGuiTest/menus/`.

## First stylesheet
```css
screen {
    title: "My Menu";
    rows: 3;
    background: BLACK_STAINED_GLASS_PANE;
}

.play {
    slot: 11;
    material: DIAMOND_SWORD;
    text: "Play";
    color: #55ddff;
    lore: "Click to start";
    action: command:spawn;
}
```

## Supported properties (phase 1)
- screen: `title`, `rows`, `background`
- components: `slot`, `material`, `text`, `color`, `lore`, `action`
- actions: `close`, `message:<text>`, `command:<player command>`, `open:<menu name>`

This is intentionally a CSS-inspired DSL, not browser CSS. Vanilla server GUIs are inventory-backed, so arbitrary pixel positioning, HTML layout, browser hover states, and CSS animations are not available. Later phases can add reusable classes, variables, conditions, pagination, and more layout helpers.
