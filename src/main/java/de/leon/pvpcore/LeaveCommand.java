package de.leon.pvpcore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Lets players safely leave an active duel or practice room. */
public final class LeaveCommand implements CommandExecutor {
    private final PvPCore plugin;

    public LeaveCommand(PvPCore plugin) {
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
            plugin.message(player, "&eUsage: /leave");
            return true;
        }
        if (plugin.parkour().leaveToSpawn(player)) return true;
        if (!plugin.practice().leaveToSpawn(player)) {
            plugin.message(player, "&cYou are not in a duel, practice room or parkour.");
        }
        return true;
    }
}
