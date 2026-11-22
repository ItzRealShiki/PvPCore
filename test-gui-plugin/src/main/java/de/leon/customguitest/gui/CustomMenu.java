package de.leon.customguitest.gui;

import de.leon.customguitest.CustomGuiTestPlugin;
import de.leon.customguitest.art.MapArtwork;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class CustomMenu {
    private static final int MENU_SIZE = 54;
    private final CustomGuiTestPlugin plugin;

    public CustomMenu(CustomGuiTestPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.empty());
        holder.bind(inventory);

        fillCanvas(inventory);
        addNovaTitle(inventory);

        inventory.setItem(13, plugin.artwork().item(MapArtwork.STATUS, "SYSTEM ONLINE", 0x80F0D1,
                "NOVA INTERFACE",
                "All visual buttons are server-rendered map art.",
                "No resource pack or client download required."));

        inventory.setItem(20, plugin.artwork().item(MapArtwork.PROFILE, "PROFILE", 0x81DEFF,
                "PERSONAL MODULE",
                "A custom pixel-art button.",
                "Click to test the profile interaction."));
        inventory.setItem(22, plugin.artwork().item(MapArtwork.DESIGN, "DESIGN LAB", 0xC8AEFF,
                "INTERFACE INFORMATION",
                "View the custom title and drawing system.",
                "Opens a second page with its own graphic title."));
        inventory.setItem(24, plugin.artwork().item(MapArtwork.THEME, "COLOR ATMOSPHERE", 0xE3A7FF,
                "LIVE VISUAL THEME",
                plugin.isAlternateTheme() ? "Current palette: VIOLET" : "Current palette: AURORA",
                "Click to switch the frame and background artwork."));
        inventory.setItem(31, plugin.artwork().item(MapArtwork.INTERACTION, "INTERACTION TEST", 0x8CF0C9,
                "CUSTOM INPUT",
                "Click to trigger a custom action-bar message.",
                "All inventory interaction is handled by the plugin."));
        inventory.setItem(40, plugin.artwork().item(MapArtwork.INFO, "ABOUT NOVA", 0xB7CBFF,
                "SERVER-SIDE GRAPHICS",
                "Custom map textures are delivered as map pixels.",
                "The vanilla window frame itself cannot be replaced without a pack."));
        inventory.setItem(49, plugin.artwork().item(MapArtwork.CLOSE, "CLOSE INTERFACE", 0xFF919F,
                "Close this custom interface."));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.25f);
    }

    public void openInfo(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.INFO);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.empty());
        holder.bind(inventory);

        fillCanvas(inventory);
        addLabTitle(inventory);

        inventory.setItem(13, plugin.artwork().item(MapArtwork.INFO, "THE DESIGN LAB", 0xCFB4FF,
                "NOVA / VISUAL SYSTEM",
                "The screen title is a custom image, not typed text.",
                "Each map tile is drawn by the server."));
        inventory.setItem(20, plugin.artwork().item(MapArtwork.DESIGN, "PIXEL ART BUTTONS", 0x82E2FF,
                "128 x 128 PIXEL CANVAS",
                "Each Filled Map contains original pixel artwork.",
                "The map images are reused after server restarts."));
        inventory.setItem(22, plugin.artwork().item(MapArtwork.THEME, "COLOR PALETTES", 0xE3A7FF,
                "AURORA CYAN / VIOLET",
                "A different background and border map is used",
                "when the theme is switched."));
        inventory.setItem(24, plugin.artwork().item(MapArtwork.PROFILE, "CUSTOM ICONS", 0x81DEFF,
                "ORIGINAL PIXEL SYMBOLS",
                "Profile, design, theme, interaction, info,",
                "status, back and close icons are all custom."));
        inventory.setItem(31, plugin.artwork().item(MapArtwork.INFO, "CLIENT LIMIT", 0xFFD28C,
                "THE CHEST FRAME IS VANILLA",
                "Minecraft does not let a server replace the",
                "native window font or container texture without a pack."));
        inventory.setItem(40, plugin.artwork().item(MapArtwork.STATUS, "SERVER-RENDERED", 0x8CF0C9,
                "NO RESOURCE PACK",
                "The image data is sent using vanilla maps.",
                "Every player sees the same custom art."));
        inventory.setItem(49, plugin.artwork().item(MapArtwork.BACK, "BACK TO NOVA", 0x81DEFF,
                "Return to the main interface."));
        inventory.setItem(53, plugin.artwork().item(MapArtwork.CLOSE, "CLOSE INTERFACE", 0xFF919F,
                "Close this custom interface."));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.05f);
    }

    public void refresh(Player player) {
        openMain(player);
    }

    private void fillCanvas(Inventory inventory) {
        MapArtwork panel = plugin.isAlternateTheme() ? MapArtwork.PANEL_VIOLET : MapArtwork.PANEL;
        MapArtwork edge = plugin.isAlternateTheme() ? MapArtwork.EDGE_VIOLET : MapArtwork.EDGE;
        MapArtwork corner = plugin.isAlternateTheme() ? MapArtwork.ORNAMENT_VIOLET : MapArtwork.ORNAMENT;
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean horizontalEdge = row == 0 || row == 5;
            boolean verticalEdge = column == 0 || column == 8;
            MapArtwork texture = horizontalEdge || verticalEdge ? edge : panel;
            if ((row == 0 || row == 5) && (column == 0 || column == 8)) texture = corner;
            inventory.setItem(slot, plugin.artwork().tile(texture));
        }
    }

    private void addNovaTitle(Inventory inventory) {
        inventory.setItem(1, plugin.artwork().tile(MapArtwork.ORNAMENT));
        inventory.setItem(2, plugin.artwork().tile(MapArtwork.TITLE_N));
        inventory.setItem(3, plugin.artwork().tile(MapArtwork.TITLE_O));
        inventory.setItem(4, plugin.artwork().tile(MapArtwork.TITLE_V));
        inventory.setItem(5, plugin.artwork().tile(MapArtwork.TITLE_A));
        inventory.setItem(6, plugin.artwork().tile(MapArtwork.ORNAMENT));
        inventory.setItem(7, plugin.artwork().tile(MapArtwork.ORNAMENT));
    }

    private void addLabTitle(Inventory inventory) {
        inventory.setItem(1, plugin.artwork().tile(MapArtwork.ORNAMENT_VIOLET));
        inventory.setItem(2, plugin.artwork().tile(MapArtwork.TITLE_L));
        inventory.setItem(3, plugin.artwork().tile(MapArtwork.TITLE_A));
        inventory.setItem(4, plugin.artwork().tile(MapArtwork.TITLE_B));
        inventory.setItem(5, plugin.artwork().tile(MapArtwork.ORNAMENT_VIOLET));
        inventory.setItem(6, plugin.artwork().tile(MapArtwork.ORNAMENT_VIOLET));
        inventory.setItem(7, plugin.artwork().tile(MapArtwork.ORNAMENT_VIOLET));
    }
}
