package de.leon.minecraftcss.command;

import de.leon.minecraftcss.gui.CssGuiEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class McssCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CssGuiEngine engine;

    public McssCommand(JavaPlugin plugin, CssGuiEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mcss.admin")) {
                sender.sendMessage("§cYou do not have permission to reload CSS Engine menus.");
                return true;
            }
            engine.initialize();
            sender.sendMessage("§aMinecraft CSS Engine menus reloaded. Files are parsed whenever a menu opens.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("mcss.admin")) {
                sender.sendMessage("§cYou do not have permission to list CSS Engine menus.");
                return true;
            }
            sender.sendMessage("§bAvailable menus: §f" + String.join("§7, §f", engine.menuNames()));
            return true;
        }

        if (!sender.hasPermission("mcss.use")) {
            sender.sendMessage("§cYou do not have permission to use CSS Engine menus.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        String menuName = args.length >= 2 && args[0].equalsIgnoreCase("open")
                ? args[1]
                : args.length >= 1 && !args[0].equalsIgnoreCase("help") && !args[0].equalsIgnoreCase("open")
                    ? args[0]
                    : "main";

        if (args.length == 1 && args[0].equalsIgnoreCase("open")) {
            menuName = "main";
        }
        try {
            engine.open(player, menuName);
        } catch (IOException | IllegalArgumentException exception) {
            player.sendMessage("§cCould not open menu: " + exception.getMessage());
            plugin.getLogger().warning("Could not open menu '" + menuName + "': " + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(engine.menuNames());
            values.add("open");
            values.add("list");
            values.add("reload");
            return values.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return engine.menuNames().stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
