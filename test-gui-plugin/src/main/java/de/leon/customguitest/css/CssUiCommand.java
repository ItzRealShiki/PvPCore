package de.leon.customguitest.css;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.*;

public final class CssUiCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CssGuiEngine engine;

    public CssUiCommand(JavaPlugin plugin, CssGuiEngine engine) { this.plugin = plugin; this.engine = engine; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customguitest.css")) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            engine.initialize();
            sender.sendMessage("§aCSS menus reloaded from plugins/CustomGuiTest/menus.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        String name = args.length >= 2 && args[0].equalsIgnoreCase("open") ? args[1]
                : args.length >= 1 && !args[0].equalsIgnoreCase("help") ? args[0] : "main";
        try {
            engine.open(player, name);
        } catch (IOException | IllegalArgumentException exception) {
            player.sendMessage("§cCould not open menu: " + exception.getMessage());
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(engine.menuNames());
            values.add("open"); values.add("reload");
            return values.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open"))
            return engine.menuNames().stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
