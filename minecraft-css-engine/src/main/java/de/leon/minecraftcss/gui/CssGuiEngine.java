package de.leon.minecraftcss.gui;

import de.leon.minecraftcss.css.CssStyleSheet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
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
    private static final NamespacedKey INVISIBLE_MODEL = new NamespacedKey("mcss", "gui_hidden");

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

        TitleSelection titleSelection = selectTitle(screen, name);
        String title = applyPlaceholders(unquote(titleSelection.title()), player);
        CssGuiHolder holder = new CssGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Component.text(title));
        holder.setInventory(inventory);

        String defaultBackground = titleSelection.textured() ? "AIR" : "BLACK_STAINED_GLASS_PANE";
        Material background = parseMaterial(screen.getOrDefault("background", defaultBackground), Material.AIR);
        if (background != Material.AIR) {
            ItemStack filler = new ItemStack(background);
            ItemMeta fillerMeta = filler.getItemMeta();
            fillerMeta.setHideTooltip(true);
            filler.setItemMeta(fillerMeta);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        boolean hideButtonsByDefault = titleSelection.textured()
                && Boolean.parseBoolean(unquote(screen.getOrDefault("hide-buttons", "true")));

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

            Material material = parseMaterial(style.getOrDefault("item",
                    style.getOrDefault("material", "PAPER")), Material.PAPER);
            String defaultName = selector.substring(1);
            String displayName = applyPlaceholders(unquote(style.getOrDefault("text", defaultName)), player);
            String colorValue = style.get("color");
            String loreValue = style.get("lore");
            String action = applyPlaceholders(unquote(style.getOrDefault("action", "")), player);
            int amount = Math.max(1, Math.min(64, parseInt(style.getOrDefault("amount", "1"), 1)));
            boolean glow = Boolean.parseBoolean(unquote(style.getOrDefault("glow", "false")));
            String renderMode = unquote(style.getOrDefault("render", hideButtonsByDefault ? "hidden" : "normal"));
            boolean hidden = renderMode.equalsIgnoreCase("hidden") || renderMode.equalsIgnoreCase("invisible");

            ItemStack item = createItem(material, displayName, colorValue, loreValue,
                    hidden ? 1 : amount, glow, player, hidden);
            inventory.setItem(slot, item);
            holder.setAction(slot, action);
        }

        player.openInventory(inventory);
    }

    private TitleSelection selectTitle(Map<String, String> screen, String menuName) {
        String normalTitle = unquote(screen.getOrDefault("title", menuName));
        String requestedProvider = unquote(screen.getOrDefault("texture-provider", "auto"))
                .toLowerCase(Locale.ROOT);

        if (requestedProvider.equals("auto") || requestedProvider.equals("oraxen")) {
            String oraxenTitle = screen.get("title-oraxen");
            if (oraxenTitle != null && isEnabled("Oraxen")) {
                return new TitleSelection(oraxenTitle, true, "Oraxen");
            }
            if (requestedProvider.equals("oraxen")) {
                plugin.getLogger().warning("Menu asks for Oraxen textures, but Oraxen is not enabled. Using normal title.");
            }
        }

        if (requestedProvider.equals("auto") || requestedProvider.equals("itemsadder")) {
            String itemsAdderTitle = screen.get("title-itemsadder");
            if (itemsAdderTitle != null && isEnabled("ItemsAdder")) {
                return new TitleSelection(itemsAdderTitle, true, "ItemsAdder");
            }
            if (requestedProvider.equals("itemsadder")) {
                plugin.getLogger().warning("Menu asks for ItemsAdder textures, but ItemsAdder is not enabled. Using normal title.");
            }
        }

        return new TitleSelection(normalTitle, false, "none");
    }

    private boolean isEnabled(String pluginName) {
        Plugin dependency = Bukkit.getPluginManager().getPlugin(pluginName);
        return dependency != null && dependency.isEnabled();
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
                                 int amount, boolean glow, Player player, boolean hidden) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (hidden) {
            // The item remains its real Bukkit material and continues to occupy/capture the slot.
            // A client resource pack maps this item_model to minecraft:empty.
            meta.setItemModel(INVISIBLE_MODEL);
            meta.setHideTooltip(true);
            meta.displayName(Component.empty());
            meta.lore(null);
        } else {
            String resolvedName = applyPlaceholders(name, player);
            TextColor color = colorValue == null ? null : TextColor.fromHexString(unquote(colorValue));
            Component displayName = color == null
                    ? Component.text(resolvedName)
                    : Component.text(resolvedName, color);
            meta.displayName(displayName);

            if (loreValue != null) {
                String resolvedLore = applyPlaceholders(unquote(loreValue), player);
                meta.lore(Arrays.stream(resolvedLore.split("\\|", -1))
                        .map(Component::text)
                        .toList());
            }
        }

        if (glow && !hidden) {
            meta.setEnchantmentGlintOverride(true);
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

    private record TitleSelection(String title, boolean textured, String provider) {}
}
