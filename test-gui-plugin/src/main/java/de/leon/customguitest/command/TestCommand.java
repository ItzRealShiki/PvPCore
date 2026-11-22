package de.leon.customguitest.command;

import de.leon.customguitest.CustomGuiTestPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("open", "info", "close", "theme", "help");
    private final CustomGuiTestPlugin plugin;

    public TestCommand(CustomGuiTestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !player.hasPermission("customguitest.use")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        String subcommand = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "open" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    plugin.menu().openMain(player);
                }
            }
            case "info" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    plugin.menu().openInfo(player);
                }
            }
            case "close" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    player.closeInventory();
                }
            }
            case "theme" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    plugin.toggleTheme();
                    plugin.menu().refresh(player);
                    player.sendMessage(Component.text("GUI theme changed.", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("This subcommand can only be used by a player.");
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("CustomGuiTest commands", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/test", NamedTextColor.YELLOW)
                .append(Component.text(" - Open the custom GUI.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/test open|info|close|theme|help", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
