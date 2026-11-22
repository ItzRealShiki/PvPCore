package de.leon.pvpcore;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminCommand implements CommandExecutor {
    private final PvPCore plugin;
    public AdminCommand(PvPCore plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Admin.Core")) { plugin.message(player, "&cYou do not have permission."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("barrier")) {
            if (player.getInventory().getItemInOffHand().getType() == Material.BARRIER) {
                player.getInventory().setItemInOffHand(null);
                plugin.message(player, "&eBarrier visibility disabled.");
            } else {
                ItemStack viewer = new ItemStack(Material.BARRIER);
                ItemMeta meta = viewer.getItemMeta();
                meta.setDisplayName("§bBarrier Visibility");
                viewer.setItemMeta(meta);
                player.getInventory().setItemInOffHand(viewer);
                plugin.message(player, "&aHold the Barrier item to see barrier blocks. Use /admin barrier again to toggle it off.");
            }
            return true;
        }
        plugin.menus().openAdmin(player);
        return true;
    }
}