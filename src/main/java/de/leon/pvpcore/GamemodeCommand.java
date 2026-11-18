package de.leon.pvpcore;

import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class GamemodeCommand implements CommandExecutor {
    private final PvPCore plugin;
    public GamemodeCommand(PvPCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.hasCore(sender, "Admin.Core")) {
            plugin.message(sender, "&cYou do not have permission.");
            return true;
        }

        String aliasMode = switch (label.toLowerCase(Locale.ROOT)) {
            case "gms" -> "survival";
            case "gmc" -> "creative";
            case "gma" -> "adventure";
            case "gmsp" -> "spectator";
            default -> null;
        };

        if (aliasMode != null && args.length > 1) {
            plugin.message(sender, "&cUsage: /" + label + " [player]");
            return true;
        }
        if (args.length == 0 && aliasMode == null) {
            if (!(sender instanceof Player player)) return plugin.playerOnly(sender);
            plugin.menus().openGamemode(player);
            return true;
        }

        GameMode mode = parse(aliasMode != null ? aliasMode : args[0]);
        if (mode == null) {
            plugin.message(sender, "&cUse survival, creative, adventure or spectator.");
            return true;
        }

        int targetIndex = aliasMode == null ? 1 : 0;
        Player target = args.length > targetIndex
                ? Bukkit.getPlayerExact(args[targetIndex])
                : sender instanceof Player player ? player : null;
        if (target == null) {
            plugin.message(sender, "&cPlayer not found. Specify a player from console.");
            return true;
        }

        target.setGameMode(mode);
        plugin.message(target, "&aGamemode changed to &f" + mode.name().toLowerCase(Locale.ROOT) + "&a.");
        if (target != sender) plugin.message(sender, "&aChanged " + target.getName() + "'s gamemode.");
        return true;
    }

    static GameMode parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }
}
