package de.leon.pvpcore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DuelsCommand implements CommandExecutor {
    private final PvPCore plugin;

    public DuelsCommand(PvPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Member.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }
        if (args.length != 0) {
            plugin.message(player, "&eUsage: /duels");
            return true;
        }
        plugin.menus().openDuels(player);
        return true;
    }
}
