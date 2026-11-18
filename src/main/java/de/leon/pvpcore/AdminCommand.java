package de.leon.pvpcore;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class AdminCommand implements CommandExecutor {
    private final PvPCore plugin;
    public AdminCommand(PvPCore plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Admin.Core")) { plugin.message(player, "&cYou do not have permission."); return true; }
        plugin.menus().openAdmin(player);
        return true;
    }
}