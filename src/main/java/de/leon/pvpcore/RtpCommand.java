package de.leon.pvpcore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RtpCommand implements CommandExecutor {
    private final PvPCore plugin;

    public RtpCommand(PvPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            if (!plugin.hasCore(sender, "Admin.Core")) {
                plugin.message(sender, "&cYou do not have permission.");
                return true;
            }
            if (!plugin.worlds().resetArenas()) {
                plugin.message(sender, "&cAn arena reset is already running.");
                return true;
            }
            plugin.message(sender, "&aArena reset started.");
            return true;
        }
        if (args.length > 0) {
            plugin.message(sender, "&cUsage: /rtp [reset]");
            return true;
        }
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Member.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }
        plugin.menus().openRtp(player);
        return true;
    }
}