package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FightCommand implements CommandExecutor {
    private final PvPCore plugin;
    private final Map<UUID, UUID> incomingInvites = new HashMap<>();

    public FightCommand(PvPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
        if (!plugin.hasCore(player, "Member.Core")) {
            plugin.message(player, "&cYou do not have permission.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
            UUID challengerId = incomingInvites.remove(player.getUniqueId());
            Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
            if (challenger == null || !challenger.isOnline()) {
                plugin.message(player, "&cYou do not have a pending fight invitation.");
                return true;
            }
            if (plugin.practice().isFighting(player) || plugin.practice().isFighting(challenger)) {
                plugin.message(player, "&cOne of you is already in a fight.");
                return true;
            }
            plugin.practice().startFight(challenger, player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("deny")) {
            UUID challengerId = incomingInvites.remove(player.getUniqueId());
            Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
            if (challenger == null) plugin.message(player, "&cYou do not have a pending fight invitation.");
            else {
                plugin.message(player, "&7Fight invitation declined.");
                plugin.message(challenger, "&c" + player.getName() + " declined your fight invitation.");
            }
            return true;
        }
        if (args.length != 1) {
            plugin.message(player, "&eUsage: /fight <player> &7| &e/fight accept &7| &e/fight deny");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            plugin.message(player, "&cThat player is not online.");
            return true;
        }
        if (target.equals(player)) {
            plugin.message(player, "&cYou cannot fight yourself.");
            return true;
        }
        if (plugin.practice().isFighting(player) || plugin.practice().isFighting(target)) {
            plugin.message(player, "&cOne of you is already in a fight.");
            return true;
        }
        incomingInvites.put(target.getUniqueId(), player.getUniqueId());
        plugin.message(player, "&aFight invitation sent to &f" + target.getName() + "&a.");
        plugin.message(target, "&e" + player.getName() + " challenged you to a 1v1.");
        plugin.message(target, "&7Use &a/fight accept &7or &c/fight deny&7.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (incomingInvites.remove(target.getUniqueId(), player.getUniqueId())) {
                if (player.isOnline()) plugin.message(player, "&7Your fight invitation to " + target.getName() + " expired.");
                if (target.isOnline()) plugin.message(target, "&7The fight invitation expired.");
            }
        }, 600L);
        return true;
    }
}
