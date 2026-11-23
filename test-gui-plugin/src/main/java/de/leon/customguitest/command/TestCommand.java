package de.leon.customguitest.command;

import de.leon.customguitest.CustomGuiTestPlugin;
import de.leon.customguitest.gui.GuiPage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class TestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "dashboard", "gui", "help", "info", "inventory", "lab", "nova",
            "open", "profile", "settings", "showcase", "stats", "theme", "ui", "close"
    );
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

        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player != null) plugin.menu().open(player, GuiPage.HOME);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("gui") || first.equals("ui")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /test " + first + " <" +
                        String.join("|", GuiPage.ids()) + ">", NamedTextColor.YELLOW));
                return true;
            }
            return openPage(sender, args[1]);
        }

        switch (first) {
            case "open", "nova", "home", "main" -> {
                Player player = requirePlayer(sender);
                if (player != null) plugin.menu().open(player, GuiPage.HOME);
            }
            case "close" -> {
                Player player = requirePlayer(sender);
                if (player != null) player.closeInventory();
            }
            case "theme" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    plugin.toggleTheme();
                    plugin.menu().refresh(player);
                    player.sendMessage(Component.text(
                            plugin.isAlternateTheme() ? "Violet theme enabled." : "Aurora theme enabled.",
                            NamedTextColor.LIGHT_PURPLE));
                }
            }
            case "help" -> sendHelp(sender);
            default -> {
                GuiPage page = GuiPage.fromId(first);
                if (page != null) {
                    Player player = requirePlayer(sender);
                    if (player != null) plugin.menu().open(player, page);
                } else {
                    sendHelp(sender);
                }
            }
        }
        return true;
    }

    private boolean openPage(CommandSender sender, String value) {
        GuiPage page = GuiPage.fromId(value);
        if (page == null) {
            sender.sendMessage(Component.text("Unknown GUI. Available: " + String.join(", ", GuiPage.ids()),
                    NamedTextColor.RED));
            return true;
        }
        Player player = requirePlayer(sender);
        if (player != null) plugin.menu().open(player, page);
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage("This command can only be used by a player.");
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("NOVA / Custom GUI Test", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/test <nova|dashboard|profile|settings|showcase|stats|lab>",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/test gui <name>  or  /test ui <name>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/test theme | close | help", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("ui"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return GuiPage.ids().stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
