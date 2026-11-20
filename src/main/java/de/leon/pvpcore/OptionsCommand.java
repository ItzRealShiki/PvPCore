package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class OptionsCommand implements CommandExecutor, Listener {
    private static final List<Material> COLORS = List.of(
            Material.GLASS, Material.WHITE_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS, Material.BLACK_STAINED_GLASS, Material.BROWN_STAINED_GLASS,
            Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.GREEN_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS, Material.PINK_STAINED_GLASS
    );

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final PvPCore plugin;

    public OptionsCommand(PvPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Member.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.color("&8✦ ᴏᴘᴛɪᴏɴꜱ"));
        holder.inventory = inventory;
        Material selected = plugin.practice().forceFieldMaterial();
        for (int index = 0; index < COLORS.size(); index++) {
            Material material = COLORS.get(index);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            String name = material == Material.GLASS ? "Clear Glass"
                    : Arrays.stream(material.name().replace("_STAINED_GLASS", "").split("_"))
                    .map(part -> part.charAt(0) + part.substring(1).toLowerCase())
                    .reduce((a, b) -> a + " " + b).orElse("Glass");
            meta.setDisplayName(plugin.color((material == selected ? "&a✔ " : "&e") + name));
            meta.setLore(List.of(plugin.color(material == selected ? "&7Currently selected" : "&eClick to select")));
            item.setItemMeta(meta);
            inventory.setItem(index, item);
        }
        inventory.setItem(22, arenaItem());
        player.openInventory(inventory);
    }

    private ItemStack arenaItem() {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.color("&aArena Ground"));
        meta.setLore(List.of(plugin.color("&7Current: &f" + plugin.practice().arenaTheme(player).replace('_', ' ')),
                plugin.color("&eClick to change the arena theme")));
        item.setItemMeta(meta);
        return item;
    }

    private void openArenaThemes(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.color("&8✦ ᴀʀᴇɴᴀ ɢʀᴏᴜɴᴅ"));
        holder.inventory = inventory;
        Material[] icons = {Material.SAND, Material.GRASS_BLOCK, Material.END_STONE, Material.NETHERRACK,
                Material.RED_SAND, Material.PALE_MOSS_BLOCK, Material.CHERRY_LEAVES,
                Material.WARPED_NYLIUM, Material.CRIMSON_NYLIUM, Material.POPPY};
        String[] themes = {"DESERT", "PLAINS", "END", "NETHER", "BADLANDS", "PALE_GARDEN",
                "CHERRY_BLOSSOM", "WARPED", "CRIMSON", "FLOWER_PLAINS"};
        for (int i = 0; i < themes.length; i++) {
            ItemStack item = new ItemStack(icons[i]);
            ItemMeta meta = item.getItemMeta();
            boolean active = plugin.practice().arenaTheme(player).equals(themes[i]);
            meta.setDisplayName(plugin.color((active ? "&a✔ " : "&e") + themes[i].replace('_', ' ')));
            meta.setLore(List.of(plugin.color(active ? "&7Currently selected" : "&eClick to select")));
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getView().getTitle().equals(plugin.color("&8✦ ᴀʀᴇɴᴀ ɢʀᴏᴜɴᴅ"))) {
            int themeSlot = event.getSlot();
            String[] themes = {"DESERT", "PLAINS", "END", "NETHER", "BADLANDS", "PALE_GARDEN",
                    "CHERRY_BLOSSOM", "WARPED", "CRIMSON", "FLOWER_PLAINS"};
            if (themeSlot >= 0 && themeSlot < themes.length) {
                plugin.practice().setArenaTheme(player, themes[themeSlot]);
                plugin.message(player, "&aArena ground set to &e" + themes[themeSlot].replace('_', ' ') + "&a.");
                open(player);
            }
            return;
        }
        int slot = event.getSlot();
        if (slot == 22) { openArenaThemes(player); return; }
        if (slot < 0 || slot >= COLORS.size()) return;
        Material selected = COLORS.get(slot);
        plugin.practice().setForceFieldMaterial(selected);
        plugin.message(player, "&aForcefield color set to &e" + selected.name().replace("_STAINED_GLASS", "").replace('_', ' ') + "&a.");
        open(player);
    }
}
