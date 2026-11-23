package de.leon.customguitest.gui;

import de.leon.customguitest.CustomGuiTestPlugin;
import de.leon.customguitest.art.MapArtwork;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class CustomMenu {
    private static final int SIZE = 54;
    private final CustomGuiTestPlugin plugin;

    public CustomMenu(CustomGuiTestPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) { open(player, GuiPage.HOME); }
    public void openInfo(Player player) { open(player, GuiPage.LAB); }

    public void open(Player player, GuiPage page) {
        GuiHolder holder = new GuiHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.empty());
        holder.bind(inventory);

        paintCanvas(inventory);
        paintTitle(inventory, page);
        switch (page) {
            case HOME -> buildHome(inventory);
            case DASHBOARD -> buildDashboard(inventory);
            case PROFILE -> buildProfile(inventory, player);
            case SETTINGS -> buildSettings(inventory);
            case SHOWCASE -> buildShowcase(inventory);
            case STATS -> buildStats(inventory, player);
            case LAB -> buildLab(inventory);
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.55f, page == GuiPage.SETTINGS ? 1.25f : 1.05f);
    }

    public void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder) {
            open(player, holder.page());
        } else {
            openMain(player);
        }
    }

    private void paintCanvas(Inventory inventory) {
        MapArtwork panel = plugin.isAlternateTheme() ? MapArtwork.PANEL_VIOLET : MapArtwork.PANEL;
        MapArtwork edge = plugin.isAlternateTheme() ? MapArtwork.EDGE_VIOLET : MapArtwork.EDGE;
        MapArtwork corner = plugin.isAlternateTheme() ? MapArtwork.ORNAMENT_VIOLET : MapArtwork.ORNAMENT;
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean border = row == 0 || row == 5 || column == 0 || column == 8;
            MapArtwork texture = border ? edge : panel;
            if ((row == 0 || row == 5) && (column == 0 || column == 8)) texture = corner;
            inventory.setItem(slot, plugin.artwork().tile(texture));
        }
    }

    private void paintTitle(Inventory inventory, GuiPage page) {
        MapArtwork ornament = plugin.isAlternateTheme() ? MapArtwork.ORNAMENT_VIOLET : MapArtwork.ORNAMENT;
        inventory.setItem(1, plugin.artwork().tile(ornament));
        inventory.setItem(7, plugin.artwork().tile(ornament));
        String title = page.title();
        int start = Math.max(1, (9 - title.length()) / 2);
        for (int i = 0; i < title.length() && start + i < 8; i++) {
            inventory.setItem(start + i, plugin.artwork().tile(letter(title.charAt(i))));
        }
    }

    private MapArtwork letter(char value) {
        return switch (value) {
            case 'N' -> MapArtwork.TITLE_N;
            case 'O' -> MapArtwork.TITLE_O;
            case 'V' -> MapArtwork.TITLE_V;
            case 'A' -> MapArtwork.TITLE_A;
            case 'L' -> MapArtwork.TITLE_L;
            case 'B' -> MapArtwork.TITLE_B;
            case 'D' -> MapArtwork.TITLE_D;
            case 'S' -> MapArtwork.TITLE_S;
            case 'H' -> MapArtwork.TITLE_H;
            case 'P' -> MapArtwork.TITLE_P;
            case 'R' -> MapArtwork.TITLE_R;
            case 'F' -> MapArtwork.TITLE_F;
            case 'I' -> MapArtwork.TITLE_I;
            case 'T' -> MapArtwork.TITLE_T;
            case 'Y' -> MapArtwork.TITLE_Y;
            case 'E' -> MapArtwork.TITLE_E;
            case 'M' -> MapArtwork.TITLE_M;
            default -> MapArtwork.ORNAMENT;
        };
    }

    private void buildHome(Inventory inv) {
        item(inv, 13, MapArtwork.STATUS, "SYSTEM ONLINE", 0x80F0D1,
                "NOVA INTERFACE / MAIN HUB", "Seven separate interface concepts", "Choose a module below.");
        item(inv, 20, MapArtwork.PROFILE, "PLAYER PROFILE", 0x81DEFF,
                "PERSONAL MODULE", "Player identity and session details", "Open: /test profile");
        item(inv, 22, MapArtwork.DESIGN, "DASHBOARD", 0xC8AEFF,
                "CONTROL CENTER", "Status cards and quick actions", "Open: /test dashboard");
        item(inv, 24, MapArtwork.THEME, "SETTINGS", 0xE3A7FF,
                "VISUAL PREFERENCES", "Theme and interaction previews", "Open: /test settings");
        item(inv, 29, MapArtwork.INFO, "UI SHOWCASE", 0xB7CBFF,
                "CUSTOM ARTWORK GALLERY", "Preview the available pixel-art buttons", "Open: /test showcase");
        item(inv, 31, MapArtwork.INTERACTION, "ANALYTICS", 0x8CF0C9,
                "LIVE PLAYER VALUES", "Health, food, level, ping and position", "Open: /test stats");
        item(inv, 33, MapArtwork.BACK, "DESIGN LAB", 0xFFD28C,
                "HOW THIS UI WORKS", "Maps, custom title tiles and limitations", "Open: /test lab");
        item(inv, 49, MapArtwork.CLOSE, "CLOSE", 0xFF919F, "Close the interface.");
    }

    private void buildDashboard(Inventory inv) {
        item(inv, 10, MapArtwork.STATUS, "CORE STATUS", 0x80F0D1,
                "ONLINE", "The menu renderer is active.");
        item(inv, 12, MapArtwork.INTERACTION, "INTERACTION", 0x81DEFF,
                "INPUT CHANNEL READY", "Click to test an action-bar response.");
        item(inv, 14, MapArtwork.THEME, "ACTIVE PALETTE", 0xD9B4FF,
                plugin.isAlternateTheme() ? "VIOLET" : "AURORA", "Click to switch the global palette.");
        item(inv, 16, MapArtwork.INFO, "PLUGIN BUILD", 0xB7CBFF,
                "CUSTOM GUI TEST", "Standalone Paper plugin.", "No PvPCore dependency.");
        item(inv, 29, MapArtwork.PROFILE, "PLAYER PROFILE", 0x81DEFF, "Open player identity.");
        item(inv, 31, MapArtwork.DESIGN, "DESIGN LAB", 0xC8AEFF, "Inspect the visual implementation.");
        item(inv, 33, MapArtwork.BACK, "HOME", 0x81DEFF, "Return to the NOVA hub.");
        item(inv, 49, MapArtwork.CLOSE, "CLOSE", 0xFF919F, "Close the interface.");
    }

    private void buildProfile(Inventory inv, Player player) {
        item(inv, 13, MapArtwork.PROFILE, "PLAYER IDENTITY", 0x81DEFF,
                player.getName(), "UUID " + player.getUniqueId().toString().substring(0, 8));
        item(inv, 20, MapArtwork.STATUS, "SESSION STATUS", 0x80F0D1,
                "CONNECTED", "Current world: " + player.getWorld().getName());
        item(inv, 22, MapArtwork.INFO, "NETWORK", 0xB7CBFF,
                "PING: " + Math.max(0, player.getPing()) + " MS", "Live value from the server.");
        item(inv, 24, MapArtwork.THEME, "INTERFACE THEME", 0xD9B4FF,
                plugin.isAlternateTheme() ? "VIOLET" : "AURORA", "Change it in /test settings.");
        item(inv, 30, MapArtwork.DESIGN, "PLAYER LEVEL", 0xC8AEFF,
                "LEVEL " + player.getLevel(), "Current experience level.");
        item(inv, 32, MapArtwork.INTERACTION, "FOOD LEVEL", 0x8CF0C9,
                player.getFoodLevel() + " / 20", "Current food value.");
        item(inv, 49, MapArtwork.BACK, "BACK TO HOME", 0x81DEFF, "Return to NOVA.");
    }

    private void buildSettings(Inventory inv) {
        item(inv, 11, MapArtwork.THEME, "COLOR ATMOSPHERE", 0xD9B4FF,
                plugin.isAlternateTheme() ? "VIOLET ACTIVE" : "AURORA ACTIVE",
                "Click to switch the palette for all screens.");
        item(inv, 13, MapArtwork.INTERACTION, "SOUND PREVIEW", 0x81DEFF,
                "CLICK TO PREVIEW", "Plays the interface sound.");
        item(inv, 15, MapArtwork.INFO, "RESOURCE PACK MODE", 0xB7CBFF,
                "SERVER-RENDERED MAP ART", "This build needs no resource pack.",
                "A future pack-based UI can replace this renderer.");
        item(inv, 29, MapArtwork.STATUS, "PERSISTENCE", 0x80F0D1,
                "MAP IDS SAVED", "Map identifiers are stored in config.yml.");
        item(inv, 31, MapArtwork.DESIGN, "VISUAL STYLE", 0xC8AEFF,
                "PIXEL ART", "Custom title tiles and button artwork.");
        item(inv, 33, MapArtwork.BACK, "HOME", 0x81DEFF, "Return to NOVA.");
        item(inv, 49, MapArtwork.BACK, "BACK", 0x81DEFF, "Return to NOVA.");
        item(inv, 53, MapArtwork.CLOSE, "CLOSE", 0xFF919F, "Close the interface.");
    }

    private void buildShowcase(Inventory inv) {
        item(inv, 10, MapArtwork.PROFILE, "PROFILE BUTTON", 0x81DEFF, "Original 128 x 128 map artwork.");
        item(inv, 12, MapArtwork.DESIGN, "DESIGN BUTTON", 0xC8AEFF, "Layered panel pixel art.");
        item(inv, 14, MapArtwork.THEME, "THEME BUTTON", 0xE3A7FF, "Color-wheel inspired icon.");
        item(inv, 16, MapArtwork.INTERACTION, "ACTION BUTTON", 0x8CF0C9, "Energy-bolt artwork.");
        item(inv, 19, MapArtwork.STATUS, "STATUS BUTTON", 0x80F0D1, "Signal-line artwork.");
        item(inv, 21, MapArtwork.INFO, "INFO BUTTON", 0xB7CBFF, "Information symbol artwork.");
        item(inv, 23, MapArtwork.BACK, "BACK BUTTON", 0x81DEFF, "Navigation arrow artwork.");
        item(inv, 25, MapArtwork.CLOSE, "CLOSE BUTTON", 0xFF919F, "Close symbol artwork.");
        item(inv, 30, MapArtwork.ORNAMENT, "AURORA ORNAMENT", 0x81DEFF, "Decorative frame tile.");
        item(inv, 32, MapArtwork.EDGE, "AURORA EDGE", 0x81DEFF, "Border texture tile.");
        item(inv, 34, MapArtwork.PANEL, "AURORA PANEL", 0x81DEFF, "Background texture tile.");
        item(inv, 40, MapArtwork.ORNAMENT_VIOLET, "VIOLET ORNAMENT", 0xD9B4FF, "Alternate decorative tile.");
        item(inv, 42, MapArtwork.EDGE_VIOLET, "VIOLET EDGE", 0xD9B4FF, "Alternate border tile.");
        item(inv, 44, MapArtwork.PANEL_VIOLET, "VIOLET PANEL", 0xD9B4FF, "Alternate background tile.");
        item(inv, 49, MapArtwork.BACK, "BACK TO HOME", 0x81DEFF, "Return to NOVA.");
    }

    private void buildStats(Inventory inv, Player player) {
        item(inv, 10, MapArtwork.STATUS, "HEALTH", 0x80F0D1,
                String.format(java.util.Locale.ROOT, "%.1f HP", player.getHealth()),
                "Current health value.");
        item(inv, 12, MapArtwork.PROFILE, "FOOD", 0x81DEFF,
                player.getFoodLevel() + " / 20", "Current food level.");
        item(inv, 14, MapArtwork.DESIGN, "EXPERIENCE", 0xC8AEFF,
                "LEVEL " + player.getLevel(), "Current experience level.");
        item(inv, 16, MapArtwork.INTERACTION, "PING", 0x8CF0C9,
                Math.max(0, player.getPing()) + " MS", "Current server ping.");
        item(inv, 29, MapArtwork.INFO, "WORLD", 0xB7CBFF,
                player.getWorld().getName(), "Current world.");
        item(inv, 31, MapArtwork.THEME, "POSITION", 0xD9B4FF,
                "X " + player.getLocation().getBlockX(),
                "Y " + player.getLocation().getBlockY(),
                "Z " + player.getLocation().getBlockZ(),
                "Click to refresh these values.");
        item(inv, 33, MapArtwork.BACK, "HOME", 0x81DEFF, "Return to NOVA.");
        item(inv, 49, MapArtwork.BACK, "BACK", 0x81DEFF, "Return to NOVA.");
    }

    private void buildLab(Inventory inv) {
        item(inv, 10, MapArtwork.DESIGN, "CUSTOM TITLE", 0xC8AEFF,
                "BITMAP LETTER TILES", "The header is drawn from custom map images.",
                "No normal text is used for the large title.");
        item(inv, 12, MapArtwork.PROFILE, "CUSTOM BUTTONS", 0x81DEFF,
                "ORIGINAL PIXEL ART", "Every displayed icon is a dedicated map texture.");
        item(inv, 14, MapArtwork.THEME, "THEME SYSTEM", 0xD9B4FF,
                "AURORA / VIOLET", "One palette switch updates every GUI.");
        item(inv, 16, MapArtwork.INFO, "NAVIGATION", 0xB7CBFF,
                "SEVEN SCREENS", "All screens share the same frame and controls.");
        item(inv, 29, MapArtwork.STATUS, "SERVER RENDERED", 0x80F0D1,
                "VANILLA MAP CARRIER", "Pixels are sent through normal map data.");
        item(inv, 31, MapArtwork.INTERACTION, "CLIENT LIMIT", 0xFFD28C,
                "WINDOW FRAME IS VANILLA", "A plugin alone cannot replace the client",
                "inventory background or native font.");
        item(inv, 33, MapArtwork.BACK, "HOME", 0x81DEFF, "Return to NOVA.");
        item(inv, 49, MapArtwork.BACK, "BACK", 0x81DEFF, "Return to NOVA.");
        item(inv, 53, MapArtwork.CLOSE, "CLOSE", 0xFF919F, "Close the interface.");
    }

    private void item(Inventory inventory, int slot, MapArtwork artwork, String name, int color, String... lore) {
        inventory.setItem(slot, plugin.artwork().item(artwork, name, color, lore));
    }
}
