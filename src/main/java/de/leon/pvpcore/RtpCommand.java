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
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Member.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }
        if (args.length == 0) {
            plugin.worlds().randomTeleport(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("menu")) {
            plugin.menus().openRtp(player);
            return true;
        }
        if (args.length == 1 && plugin.worlds().isWorldKey(args[0])) {
            plugin.worlds().teleport(player, args[0].toLowerCase());
            return true;
        }
        plugin.message(player, "&7Usage: &f/rtp &7| &f/rtp <plains|desert|snow|badlands|menu>");
        return true;
    }
}
