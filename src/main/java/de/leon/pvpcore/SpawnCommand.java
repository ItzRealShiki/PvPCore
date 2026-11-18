package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnCommand implements CommandExecutor {
    private final PvPCore plugin;

    public SpawnCommand(PvPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
            plugin.worlds().teleportToSpawn(player);
            return true;
        }

        if (args.length != 1) {
            plugin.message(sender, "&cUsage: /spawn [player]");
            return true;
        }
        if (!plugin.hasCore(sender, "Admin.Core")) {
            plugin.message(sender, "&cYou do not have permission.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.message(sender, "&cThat player is not online.");
            return true;
        }
        plugin.worlds().teleportToSpawn(target);
        if (sender != target) plugin.message(sender, "&aTeleported &f" + target.getName() + " &ato spawn.");
        return true;
    }
}
