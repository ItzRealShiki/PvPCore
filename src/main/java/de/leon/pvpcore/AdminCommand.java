package de.leon.pvpcore;

import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminCommand implements CommandExecutor {
    private static final String BARRIER_VIEWER_NAME = "§bBarrier Visibility";
    private final PvPCore plugin;

    public AdminCommand(PvPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Admin.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("barrier")) {
            int viewerSlot = findViewerItem(player);
            if (viewerSlot >= 0) {
                player.getInventory().setItem(viewerSlot, null);
                plugin.message(player, "&eBarrier visibility item removed.");
                return true;
            }

            int emptySlot = player.getInventory().firstEmpty();
            if (emptySlot < 0) {
                plugin.message(player, "&cYour inventory is full. Make one slot free first.");
                return true;
            }
            ItemStack viewer = new ItemStack(Material.BARRIER);
            ItemMeta meta = viewer.getItemMeta();
            meta.setDisplayName(BARRIER_VIEWER_NAME);
            viewer.setItemMeta(meta);
            player.getInventory().setItem(emptySlot, viewer);
            plugin.message(player, "&aBarrier viewer added. Hold it in your main hand or offhand to see barrier blocks.");
            return true;
        }
        plugin.menus().openAdmin(player);
        return true;
    }

    private int findViewerItem(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != Material.BARRIER || !item.hasItemMeta()) continue;
            if (BARRIER_VIEWER_NAME.equals(item.getItemMeta().getDisplayName())) return slot;
        }
        return -1;
    }
}
