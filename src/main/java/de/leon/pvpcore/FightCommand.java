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

    public DuelCommand(PvPCore plugin) {
        this.plugin = plugin;
    }

    public Player incomingFrom(Player player) {
        UUID challengerId = incomingInvites.get(player.getUniqueId());
        return challengerId == null ? null : Bukkit.getPlayer(challengerId);
    }

    public int cancelOutgoingInvites(Player player) {
        int before = incomingInvites.size();
        incomingInvites.entrySet().removeIf(entry -> entry.getValue().equals(player.getUniqueId()));
        return before - incomingInvites.size();
    }

    public int outgoingInviteCount(Player player) {
        return (int) incomingInvites.values().stream().filter(id -> id.equals(player.getUniqueId())).count();
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
                plugin.message(player, "&cYou do not have a pending duel invitation.");
                return true;
            }
            if (plugin.practice().isFighting(player) || plugin.practice().isFighting(challenger)) {
                plugin.message(player, "&cOne of you is already in a duel.");
                return true;
            }
            plugin.practice().startFight(challenger, player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("deny")) {
            UUID challengerId = incomingInvites.remove(player.getUniqueId());
            Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
            if (challenger == null) plugin.message(player, "&cYou do not have a pending duel invitation.");
            else {
                plugin.message(player, "&7Duel invitation declined.");
                plugin.message(challenger, "&c" + player.getName() + " declined your duel invitation.");
            }
            return true;
        }
        if (args.length != 1) {
            plugin.message(player, "&eUsage: /duel <player> &7| &e/duel accept &7| &e/duel deny");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            plugin.message(player, "&cThat player is not online.");
            return true;
        }
        if (target.equals(player)) {
            plugin.message(player, "&cYou cannot duel yourself.");
            return true;
        }
        if (plugin.practice().isFighting(player) || plugin.practice().isFighting(target)) {
            plugin.message(player, "&cOne of you is already in a duel.");
            return true;
        }
        incomingInvites.put(target.getUniqueId(), player.getUniqueId());
        plugin.message(player, "&aDuel invitation sent to &f" + target.getName() + "&a.");
        plugin.message(target, "&e" + player.getName() + " challenged you to a 1v1.");
        plugin.message(target, "&7Use &a/duel accept &7or &c/duel deny&7.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (incomingInvites.remove(target.getUniqueId(), player.getUniqueId())) {
                if (player.isOnline()) plugin.message(player, "&7Your duel invitation to " + target.getName() + " expired.");
                if (target.isOnline()) plugin.message(target, "&7The duel invitation expired.");
            }
        }, 600L);
        return true;
    }
}
