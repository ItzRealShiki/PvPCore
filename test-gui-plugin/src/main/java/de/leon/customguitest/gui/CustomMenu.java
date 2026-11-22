package de.leon.customguitest.gui;

import de.leon.customguitest.CustomGuiTestPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.util.List;

public final class CustomMenu {
    private static final int MENU_SIZE = 54;
    private final CustomGuiTestPlugin plugin;

    public CustomMenu(CustomGuiTestPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, mainTitle());
        holder.bind(inventory);

        fillBackground(inventory);
        addHeader(inventory);
        inventory.setItem(20, item(Material.DIAMOND, "PROFILE PREVIEW", 0x65D9FF,
                "A demo button inside the custom menu.",
                "Click to show a small player greeting."));
        inventory.setItem(22, item(Material.WRITABLE_BOOK, "ABOUT THIS GUI", 0xB69CFF,
                "Open the information page.",
                "This page also has its own custom title."));
        inventory.setItem(24, item(Material.AMETHYST_SHARD, "CHANGE THEME", 0xE3A6FF,
                "Switch between the cyan and violet themes.",
                "The menu is redrawn instantly."));
        inventory.setItem(31, item(Material.ENDER_EYE, "INTERACTION TEST", 0x72F0C1,
                "Click to test a menu interaction.",
                "The click is handled by the plugin."));
        inventory.setItem(49, item(Material.BARRIER, "CLOSE MENU", 0xFF7777,
                "Close this custom interface."));

        player.openInventory(inventory);
    }

    public void openInfo(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.INFO);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, infoTitle());
        holder.bind(inventory);

        fillBackground(inventory);
        inventory.setItem(13, item(Material.NETHER_STAR, "CUSTOM GUI TEST", 0xE7D5FF,
                "A standalone Paper plugin.",
                "No client-side resource pack is needed for this layout."));
        inventory.setItem(20, item(Material.CHEST, "CUSTOM LAYOUT", 0x65D9FF,
                "The plugin decides which item appears in every slot.",
                "Decorative borders and button positions are configurable."));
        inventory.setItem(22, item(Material.NAME_TAG, "CUSTOM TITLE", 0xB69CFF,
                "The inventory title is an Adventure Component.",
                "It can use custom colors and formatting."));
        inventory.setItem(24, item(Material.PAINTING, "CLIENT-SIDE LIMIT", 0xFFC978,
                "The chest window background itself is still vanilla.",
                "Changing the actual chest texture needs a resource pack."));
        inventory.setItem(49, item(Material.ARROW, "BACK", 0x65D9FF,
                "Return to the main menu."));
        inventory.setItem(53, item(Material.BARRIER, "CLOSE MENU", 0xFF7777,
                "Close this custom interface."));

        player.openInventory(inventory);
    }

    public void refresh(Player player) {
        openMain(player);
    }

    private void fillBackground(Inventory inventory) {
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            boolean edge = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            Material material;

            if (!edge) {
                material = Material.BLACK_STAINED_GLASS_PANE;
            } else if ((slot + (plugin.isAlternateTheme() ? 1 : 0)) % 2 == 0) {
                material = plugin.isAlternateTheme()
                        ? Material.PURPLE_STAINED_GLASS_PANE
                        : Material.CYAN_STAINED_GLASS_PANE;
            } else {
                material = plugin.isAlternateTheme()
                        ? Material.MAGENTA_STAINED_GLASS_PANE
                        : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            }

            inventory.setItem(slot, pane(material));
        }
    }

    private void addHeader(Inventory inventory) {
        inventory.setItem(4, item(Material.NETHER_STAR, "✦ CUSTOM INTERFACE ✦", 0xE7D5FF,
                "A GUI created entirely by the plugin.",
                "Explore the buttons below."));
        inventory.setItem(13, item(Material.SEA_LANTERN, "WELCOME", 0x72F0C1,
                "Six rows. Custom title. Custom button layout.",
                "No resource pack required for this test."));
        inventory.setItem(40, item(Material.GLOW_INK_SAC, "VISUAL THEME", 0x65D9FF,
                plugin.isAlternateTheme() ? "Current theme: Violet" : "Current theme: Cyan",
                "Use the amethyst button to switch."));
    }

    private Component mainTitle() {
        return Component.text("✦ ", color(0x65D9FF))
                .append(Component.text("CUSTOM", color(0xA5CFFF)).decorate(TextDecoration.BOLD))
                .append(Component.text(" INTERFACE ", color(0xD7B7FF)).decorate(TextDecoration.BOLD))
                .append(Component.text("✦", color(0xB69CFF)))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component infoTitle() {
        return Component.text("✧ ", color(0xB69CFF))
                .append(Component.text("INTERFACE DETAILS", color(0xE7D5FF)).decorate(TextDecoration.BOLD))
                .append(Component.text(" ✧", color(0x65D9FF)))
                .decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, int hex, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color(hex))
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = java.util.Arrays.stream(loreLines)
                .map(line -> Component.text(line, color(0xB8C4D8))
                        .decoration(TextDecoration.ITALIC, false))
                .toList();
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private TextColor color(int hex) {
        return TextColor.color(hex);
    }
}
