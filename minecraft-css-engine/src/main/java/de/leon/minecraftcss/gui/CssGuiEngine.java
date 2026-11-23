package de.leon.minecraftcss.gui;

import de.leon.minecraftcss.css.CssStyleSheet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class CssGuiEngine {
    private final JavaPlugin plugin;
    private final Path menusDirectory;

    public CssGuiEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menusDirectory = plugin.getDataFolder().toPath().resolve("menus");
    }

    public void initialize() {
        try {
            Files.createDirectories(menusDirectory);
            Path example = menusDirectory.resolve("main.mcss");
            if (!Files.exists(example)) {
                try (var input = plugin.getResource("menus/main.mcss")) {
                    if (input != null) Files.copy(input, example);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not prepare menu directory: " + exception.getMessage());
        }
    }

    public Set<String> menuNames() {
        try (var files = Files.list(menusDirectory)) {
            Set<String> names = new TreeSet<>();
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(file -> file.toLowerCase(Locale.ROOT).endsWith(".mcss"))
                    .map(file -> file.substring(0, file.length() - 5))
                    .forEach(names::add);
            return names;
        } catch (IOException exception) {
            return Set.of();
        }
    }

    public void open(Player player, String name) throws IOException {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Menu names may only contain letters, numbers, '_' and '-'.");
        }

        Path file = menusDirectory.resolve(name + ".mcss").normalize();
        if (!file.startsWith(menusDirectory) || !Files.isRegularFile(file)) {
            throw new NoSuchFileException("Menu not found: " + name);
        }

        CssStyleSheet sheet = CssStyleSheet.parse(Files.readString(file, StandardCharsets.UTF_8));
        Map<String, String> screen = sheet.stylesFor("screen");

        int rows = parseInt(screen.getOrDefault("rows", "3"), 3);
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("The 'rows' property must be between 1 and 6.");
        }

        String title = applyPlaceholders(unquote(screen.getOrDefault("title", name)), player);
        CssGuiHolder holder = new CssGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Component.text(title));
        holder.setInventory(inventory);

        Material background = parseMaterial(screen.getOrDefault("background", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE);
        if (background != Material.AIR) {
            ItemStack filler = createItem(background, " ", null, null, 1, false, player);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        for (CssStyleSheet.Rule rule : sheet.rules()) {
            String selector = rule.selector().trim();
            if (selector.equalsIgnoreCase("screen") || !(selector.startsWith(".") || selector.startsWith("#"))) {
                continue;
            }

            Map<String, String> style = rule.declarations();
            if (!style.containsKey("slot")) continue;

            int slot = parseInt(style.get("slot"), -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                plugin.getLogger().warning("Ignored out-of-range slot in " + name + ".mcss: " + style.get("slot"));
                continue;
            }

            Material material = parseMaterial(style.getOrDefault("material", "PAPER"), Material.PAPER);
            String defaultName = selector.substring(1);
            String displayName = applyPlaceholders(unquote(style.getOrDefault("text", defaultName)), player);
            String colorValue = style.get("color");
            String loreValue = style.get("lore");
            String action = applyPlaceholders(unquote(style.getOrDefault("action", "")), player);
            int amount = Math.max(1, Math.min(64, parseInt(style.getOrDefault("amount", "1"), 1)));
            boolean glow = Boolean.parseBoolean(unquote(style.getOrDefault("glow", "false")));

            ItemStack item = createItem(material, displayName, colorValue, loreValue, amount, glow, player);
            inventory.setItem(slot, item);
            holder.setAction(slot, action);
        }

        player.openInventory(inventory);
    }

    public void performAction(Player player, String action) {
        if (action == null || action.isBlank()) return;

        String value = action.trim();
        if (value.equalsIgnoreCase("close")) {
            player.closeInventory();
            return;
        }
        if (startsWithIgnoreCase(value, "message:")) {
            player.sendMessage(Component.text(applyPlaceholders(value.substring(8).trim(), player)));
            return;
        }
        if (startsWithIgnoreCase(value, "command:")) {
            String command = value.substring(8).trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isBlank()) player.performCommand(applyPlaceholders(command, player));
            return;
        }
        if (startsWithIgnoreCase(value, "open:")) {
            String target = value.substring(5).trim();
            try {
                open(player, target);
            } catch (IOException | IllegalArgumentException exception) {
                player.sendMessage(Component.text("Could not open menu '" + target + "': " + exception.getMessage()));
            }
            return;
        }
        player.sendMessage(Component.text("Unknown CSS Engine action: " + value));
    }

    private ItemStack createItem(Material material, String name, String colorValue, String loreValue,
                                 int amount, boolean glow, Player player) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        String resolvedName = applyPlaceholders(name, player);
        TextColor color = colorValue == null ? null : TextColor.fromHexString(unquote(colorValue));
        Component displayName = color == null ? Component.text(resolvedName) : Component.text(resolvedName, color);
        meta.displayName(displayName);

        if (loreValue != null) {
            String resolvedLore = applyPlaceholders(unquote(loreValue), player);
            meta.lore(Arrays.stream(resolvedLore.split("\\|", -1))
                    .map(line -> Component.text(line))
                    .toList());
        }
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private Material parseMaterial(String raw, Material fallback) {
        Material parsed = Material.matchMaterial(unquote(raw).toUpperCase(Locale.ROOT));
        return parsed == null ? fallback : parsed;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(unquote(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String unquote(String raw) {
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }
        return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String applyPlaceholders(String input, Player player) {
        return input
                .replace("%player%", player.getName())
                .replace("%world%", player.getWorld().getName())
                .replace("%online%", Integer.toString(Bukkit.getOnlinePlayers().size()))
                .replace("%max_players%", Integer.toString(Bukkit.getMaxPlayers()))
                .replace("%x%", Integer.toString(player.getLocation().getBlockX()))
                .replace("%y%", Integer.toString(player.getLocation().getBlockY()))
                .replace("%z%", Integer.toString(player.getLocation().getBlockZ()));
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
