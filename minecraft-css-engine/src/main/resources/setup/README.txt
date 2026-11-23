Minecraft CSS Engine - resource-pack integration setup

This folder is exported by the plugin to plugins/MinecraftCSSEngine/setup/ on startup.

Use ONE pack provider:

ITEMSADDER
1. Copy the folder "itemsadder/contents/mcss" into plugins/ItemsAdder/contents/mcss/.
2. Put your GUI background PNG at plugins/ItemsAdder/contents/mcss/resourcepack/assets/mcss/textures/gui/dashboard.png.
3. Run /iazip.

ORAXEN
1. Copy "oraxen/glyphs/mcss.yml" into plugins/Oraxen/glyphs/mcss.yml.
2. Copy "oraxen/pack/assets/mcss/items/gui_hidden.json" into plugins/Oraxen/pack/assets/mcss/items/gui_hidden.json.
3. Put your GUI background PNG at plugins/Oraxen/pack/textures/gui/dashboard.png.
4. Run /oraxen reload all.

The PNG is your actual GUI artwork. It must include the visual button designs at the same positions as the inventory slots used in main.mcss. The plugin intentionally does not invent a default image; use your own PNG.

The hidden item model is required for the invisible button click-targets. The items remain real Bukkit ItemStacks in their slots and their click actions still work.
