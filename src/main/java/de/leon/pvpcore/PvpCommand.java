package de.leon.pvpcore;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class PvpCommand implements CommandExecutor {
    private final PvPCore plugin;
    public PvpCommand(PvPCore plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Member.Core")) { plugin.message(player, "&cYou do not have permission."); return true; }
        plugin.menus().openPvp(player);
        return true;
    }
}
